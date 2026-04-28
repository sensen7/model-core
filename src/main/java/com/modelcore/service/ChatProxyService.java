package com.modelcore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelcore.entity.ApiCallLog;
import com.modelcore.exception.ChatProxyException;
import com.modelcore.provider.ProviderClient;
import com.modelcore.security.ApiKeyPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 代理转发服务层。
 * <p>
 * 承担从 Controller 下沉的全部业务逻辑：
 * 前置检查（限流、月用量、预扣）→ 上游转发 → Token 统计 → 费用计算 → 审计日志。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatProxyService {

    private final LoadBalancedChatService chatService;
    private final TokenDeductionService deductionService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /** 默认预估费用（美元），用于预扣。实际费用在响应结束后二次确认 */
    private static final BigDecimal DEFAULT_ESTIMATED_COST = new BigDecimal("0.0100");

    // ==================== 公开方法 ====================

    /**
     * 统一前置检查：限流 → 月用量 → 预扣余额。
     * 检查失败抛出 {@link ChatProxyException}，携带对应 HTTP 状态码。
     *
     * @param principal 当前 API Key 主体
     */
    public void preCheck(ApiKeyPrincipal principal) {
        Long apiKeyId = principal.getApiKeyId();
        Long tenantId = principal.getTenantId();

        // 1. 限流检查
        if (!checkRateLimit(principal)) {
            throw new ChatProxyException(429, "请求频率超出限制，请稍后重试");
        }

        // 2. 月用量上限检查
        if (!deductionService.checkMonthlyLimit(apiKeyId, principal.getMonthlyLimit())) {
            throw new ChatProxyException(429, "API Key 本月用量已达上限");
        }

        // 3. 预扣租户余额
        BigDecimal remaining = deductionService.preDeduct(tenantId, DEFAULT_ESTIMATED_COST);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            throw new ChatProxyException(402, "余额不足，请充值后重试");
        }
    }

    /**
     * 非流式聊天补全。
     * 调用前须已通过 {@link #preCheck}。
     *
     * @param requestBody 请求体
     * @param principal   当前 API Key 主体
     * @return 上游响应 JSON 字符串
     */
    public String chat(Map<String, Object> requestBody, ApiKeyPrincipal principal) {
        Long apiKeyId = principal.getApiKeyId();
        Long tenantId = principal.getTenantId();
        String model = (String) requestBody.getOrDefault("model", "unknown");
        String requestSummary = truncate(requestBody.toString(), 500);
        long startTime = System.currentTimeMillis();
        AtomicReference<String> providerName = new AtomicReference<>("unknown");

        try {
            String responseJson = chatService.chat(new HashMap<>(requestBody), providerName);
            long duration = System.currentTimeMillis() - startTime;

            int prompt, completion, total;
            try {
                JsonNode usage = objectMapper.readTree(responseJson).path("usage");
                prompt = usage.path("prompt_tokens").asInt(0);
                completion = usage.path("completion_tokens").asInt(0);
                total = usage.path("total_tokens").asInt(0);
            } catch (Exception ignored) {
                prompt = 0; completion = 0; total = 0;
            }

            final int pt = prompt, ct = completion, tt = total;
            BigDecimal actualCost = calcCostFromProvider(providerName.get(), pt, ct);

            deductionService.confirmDeduction(tenantId, DEFAULT_ESTIMATED_COST, actualCost, apiKeyId);
            deductionService.incrementMonthlyUsage(apiKeyId, actualCost);
            safeLog(() -> auditLogService.saveLog(ApiCallLog.builder()
                    .tenantId(tenantId).apiKeyId(apiKeyId).model(model)
                    .promptTokens(pt).completionTokens(ct).totalTokens(tt)
                    .cost(actualCost).provider(providerName.get()).duration(duration)
                    .status("SUCCESS").requestBody(requestSummary)
                    .responseBody(truncate(responseJson, 1000))
                    .build()));

            return responseJson;

        } catch (Exception e) {
            final long duration = System.currentTimeMillis() - startTime;
            log.error("非流式请求失败: {}", e.getMessage());
            safeLog(() -> auditLogService.saveLog(ApiCallLog.builder()
                    .tenantId(tenantId).apiKeyId(apiKeyId).model(model)
                    .promptTokens(0).completionTokens(0).totalTokens(0)
                    .cost(BigDecimal.ZERO).provider(providerName.get()).duration(duration)
                    .status("FAILED").requestBody(requestSummary)
                    .errorMessage(truncate(e.getMessage(), 500))
                    .build()));
            deductionService.refund(tenantId, DEFAULT_ESTIMATED_COST);
            throw new ChatProxyException(502, "上游服务不可用: " + e.getMessage());
        }
    }

    /**
     * 流式聊天补全。
     * 在虚拟线程中执行上游调用和 SSE 推送，不阻塞 Servlet 线程。
     * 调用前须已在主线程通过 {@link #preCheck}。
     *
     * @param requestBody 请求体
     * @param principal   当前 API Key 主体
     * @param emitter     由 Controller 创建的 SseEmitter
     */
    public void streamChat(Map<String, Object> requestBody, ApiKeyPrincipal principal, SseEmitter emitter) {
        Long apiKeyId = principal.getApiKeyId();
        Long tenantId = principal.getTenantId();
        String model = (String) requestBody.getOrDefault("model", "unknown");
        String requestSummary = truncate(requestBody.toString(), 500);

        Thread.ofVirtual().name("sse-" + apiKeyId).start(() -> {
            long startTime = System.currentTimeMillis();
            AtomicReference<String> providerName = new AtomicReference<>("unknown");
            AtomicInteger promptTokens = new AtomicInteger(0);
            AtomicInteger completionTokens = new AtomicInteger(0);
            AtomicInteger totalTokens = new AtomicInteger(0);
            StringBuilder responseContent = new StringBuilder();

            try {
                chatService.streamChat(new HashMap<>(requestBody), providerName,
                        data -> {
                            extractUsage(data, promptTokens, completionTokens, totalTokens);
                            extractDeltaContent(data, responseContent);
                            try {
                                emitter.send(SseEmitter.event().data(data));
                            } catch (Exception e) {
                                log.warn("SSE 推送失败: {}", e.getMessage());
                            }
                        },
                        () -> {
                            long duration = System.currentTimeMillis() - startTime;
                            BigDecimal actualCost = calcCostFromProvider(
                                    providerName.get(), promptTokens.get(), completionTokens.get());
                            deductionService.confirmDeduction(tenantId, DEFAULT_ESTIMATED_COST, actualCost, apiKeyId);
                            deductionService.incrementMonthlyUsage(apiKeyId, actualCost);
                            safeLog(() -> auditLogService.saveLog(ApiCallLog.builder()
                                    .tenantId(tenantId).apiKeyId(apiKeyId).model(model)
                                    .promptTokens(promptTokens.get()).completionTokens(completionTokens.get())
                                    .totalTokens(totalTokens.get())
                                    .cost(actualCost).provider(providerName.get()).duration(duration)
                                    .status("SUCCESS").requestBody(requestSummary)
                                    .responseBody(truncate(responseContent.toString(), 1000))
                                    .build()));
                            try {
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                log.warn("SSE 完成推送失败: {}", e.getMessage());
                            }
                        },
                        e -> {
                            log.error("流式请求失败: {}", e.getMessage());
                            long duration = System.currentTimeMillis() - startTime;
                            safeLog(() -> auditLogService.saveLog(ApiCallLog.builder()
                                    .tenantId(tenantId).apiKeyId(apiKeyId).model(model)
                                    .promptTokens(0).completionTokens(0).totalTokens(0)
                                    .cost(BigDecimal.ZERO).provider(providerName.get()).duration(duration)
                                    .status("FAILED").requestBody(requestSummary)
                                    .errorMessage(truncate(e.getMessage(), 500))
                                    .build()));
                            deductionService.refund(tenantId, DEFAULT_ESTIMATED_COST);
                            sendSseError(emitter, "请求处理失败: " + e.getMessage());
                        }
                );
            } catch (Exception e) {
                log.error("流式请求异常: {}", e.getMessage());
                deductionService.refund(tenantId, DEFAULT_ESTIMATED_COST);
                sendSseError(emitter, "服务内部错误: " + e.getMessage());
            }
        });
    }

    /**
     * 向量嵌入。
     * 调用前须已通过 {@link #preCheck}。
     *
     * @param requestBody 请求体
     * @param principal   当前 API Key 主体
     * @return 上游响应 JSON 字符串
     */
    public String embeddings(Map<String, Object> requestBody, ApiKeyPrincipal principal) {
        Long apiKeyId = principal.getApiKeyId();
        Long tenantId = principal.getTenantId();
        String model = (String) requestBody.getOrDefault("model", "unknown");
        String requestSummary = truncate(requestBody.toString(), 500);
        long startTime = System.currentTimeMillis();
        AtomicReference<String> providerName = new AtomicReference<>("unknown");

        try {
            String responseJson = chatService.embeddings(requestBody, providerName);
            long duration = System.currentTimeMillis() - startTime;

            int promptTokens;
            try {
                JsonNode usage = objectMapper.readTree(responseJson).path("usage");
                promptTokens = usage.path("prompt_tokens").asInt(0);
                if (promptTokens == 0) {
                    promptTokens = usage.path("total_tokens").asInt(0);
                }
            } catch (Exception ignored) {
                promptTokens = 0;
            }

            final int pt = promptTokens;
            // 复用统一费用计算，completionTokens 为 0
            BigDecimal actualCost = calcCostFromProvider(providerName.get(), pt, 0);

            deductionService.confirmDeduction(tenantId, DEFAULT_ESTIMATED_COST, actualCost, apiKeyId);
            deductionService.incrementMonthlyUsage(apiKeyId, actualCost);
            safeLog(() -> auditLogService.saveLog(ApiCallLog.builder()
                    .tenantId(tenantId).apiKeyId(apiKeyId).model(model)
                    .promptTokens(pt).completionTokens(0).totalTokens(pt)
                    .cost(actualCost).provider(providerName.get()).duration(duration)
                    .status("SUCCESS").requestBody(requestSummary)
                    .responseBody(truncate(responseJson, 1000))
                    .build()));

            return responseJson;

        } catch (Exception e) {
            final long duration = System.currentTimeMillis() - startTime;
            log.error("Embeddings 请求失败: {}", e.getMessage());
            safeLog(() -> auditLogService.saveLog(ApiCallLog.builder()
                    .tenantId(tenantId).apiKeyId(apiKeyId).model(model)
                    .promptTokens(0).completionTokens(0).totalTokens(0)
                    .cost(BigDecimal.ZERO).provider(providerName.get()).duration(duration)
                    .status("FAILED").requestBody(requestSummary)
                    .errorMessage(truncate(e.getMessage(), 500))
                    .build()));
            deductionService.refund(tenantId, DEFAULT_ESTIMATED_COST);
            throw new ChatProxyException(502, "上游服务不可用: " + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 执行限流检查（若该 Key 未配置限流则直接放行）
     */
    private boolean checkRateLimit(ApiKeyPrincipal principal) {
        Integer limit = principal.getRateLimitPerMinute();
        if (limit == null || limit <= 0) {
            return true;
        }
        return rateLimitService.isAllowed(principal.getApiKeyId(), limit);
    }

    /**
     * 根据供应商动态价格计算费用
     */
    private BigDecimal calcCostFromProvider(String providerName, int promptTokens, int completionTokens) {
        ProviderClient usedProvider = chatService.getProvider(providerName);
        BigDecimal inputPrice = usedProvider != null
                ? usedProvider.getInputPricePerToken() : new BigDecimal("0.000001");
        BigDecimal outputPrice = usedProvider != null
                ? usedProvider.getOutputPricePerToken() : new BigDecimal("0.000002");
        return BigDecimal.valueOf(promptTokens).multiply(inputPrice)
                .add(BigDecimal.valueOf(completionTokens).multiply(outputPrice))
                .setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 从 SSE 数据中提取 delta.content，拼接到 StringBuilder 用于日志记录
     */
    private void extractDeltaContent(String data, StringBuilder sb) {
        if (data == null || data.equals("[DONE]")) return;
        try {
            JsonNode choices = objectMapper.readTree(data).path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("delta").path("content");
                if (!content.isMissingNode() && content.isTextual()) {
                    sb.append(content.asText());
                }
            }
        } catch (JsonProcessingException ignored) {
        }
    }

    /**
     * 从 SSE 数据中提取 usage 信息
     */
    private void extractUsage(String data, AtomicInteger promptTokens,
                              AtomicInteger completionTokens, AtomicInteger totalTokens) {
        if (data == null || data.equals("[DONE]")) return;
        try {
            JsonNode usage = objectMapper.readTree(data).path("usage");
            if (!usage.isMissingNode()) {
                int pt = usage.path("prompt_tokens").asInt(0);
                int ct = usage.path("completion_tokens").asInt(0);
                int tt = usage.path("total_tokens").asInt(0);
                if (pt > 0) promptTokens.set(pt);
                if (ct > 0) completionTokens.set(ct);
                if (tt > 0) totalTokens.set(tt);
            }
        } catch (JsonProcessingException ignored) {
        }
    }

    /**
     * 安全执行日志保存，捕获异常防止影响主流程
     */
    private void safeLog(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("保存调用日志失败: {}", e.getMessage());
        }
    }

    /**
     * 向 SseEmitter 推送错误信息并关闭连接
     */
    private void sendSseError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(errorJson(message)));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 构造 OpenAI 兼容的错误 JSON，使用 ObjectMapper 序列化以避免注入风险
     */
    public String errorJson(String message) {
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("message", message);
            error.put("type", "api_error");
            error.put("code", "internal_error");
            Map<String, Object> wrapper = Map.of("error", error);
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            // 极端情况下的兜底
            log.error("构造错误 JSON 失败: {}", e.getMessage());
            return "{\"error\":{\"message\":\"internal error\",\"type\":\"api_error\",\"code\":\"internal_error\"}}";
        }
    }

    /**
     * 截取字符串（防止超长内容写入数据库）
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
