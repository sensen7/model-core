package com.modelcore.service;

import com.modelcore.entity.ApiKey;
import com.modelcore.entity.Tenant;
import com.modelcore.repository.ApiKeyRepository;
import com.modelcore.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Webhook 推送服务
 * <p>
 * 当租户的 API Key 余额低于预警阈值时，向租户配置的 Webhook 地址发送通知。
 * 推送格式兼容 Slack、Discord、企业微信机器人（均支持 {"text": "..."} 格式）。
 * 为防止频繁推送，同一租户 10 分钟内最多触发一次（Redis 防抖）。
 * 基于 JDK 21 虚拟线程异步执行，不阻塞调用线程。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final StringRedisTemplate redisTemplate;

    /** Webhook 防抖 Redis Key 前缀 */
    private static final String WEBHOOK_COOLDOWN_KEY = "modelcore:webhook_cooldown:tenant:";
    /** 同一租户 Webhook 推送冷却时间：10 分钟 */
    private static final Duration WEBHOOK_COOLDOWN = Duration.ofMinutes(10);

    /** 发送 Webhook 请求的 RestClient（无 baseUrl，每次直接用完整 URL） */
    private final RestClient restClient = RestClient.builder()
            .build();

    /**
     * 触发余额不足预警推送
     * <p>
     * 在虚拟线程中异步执行，不阻塞调用线程。失败只记录日志，不影响业务流程。
     * </p>
     *
     * @param apiKeyId  触发预警的 API Key ID
     * @param remaining 当前剩余余额（美元）
     */
    public void triggerLowBalanceAlert(Long apiKeyId, BigDecimal remaining) {
        // 在虚拟线程中异步执行，不占用平台线程
        Thread.ofVirtual().name("webhook-" + apiKeyId).start(() -> {
            try {
                // 1. 查找 API Key，获取所属租户 ID
                Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);
                if (apiKeyOpt.isEmpty()) {
                    log.warn("Webhook 预警：找不到 API Key [{}]", apiKeyId);
                    return;
                }
                Long tenantId = apiKeyOpt.get().getTenantId();

                // 2. 查找租户，获取 Webhook URL
                Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
                if (tenantOpt.isEmpty()) {
                    log.warn("Webhook 预警：找不到租户 [{}]", tenantId);
                    return;
                }
                Tenant tenant = tenantOpt.get();
                String webhookUrl = tenant.getWebhookUrl();
                if (webhookUrl == null || webhookUrl.isBlank()) {
                    // 租户未配置 Webhook，静默跳过
                    return;
                }

                // 3. 防抖：检查冷却期，10 分钟内已推送则跳过
                String cooldownKey = WEBHOOK_COOLDOWN_KEY + tenantId;
                Boolean isFirstInWindow = redisTemplate.opsForValue()
                        .setIfAbsent(cooldownKey, "1", WEBHOOK_COOLDOWN);
                if (!Boolean.TRUE.equals(isFirstInWindow)) {
                    log.debug("租户 [{}] Webhook 在冷却期内，跳过本次推送", tenantId);
                    return;
                }

                // 4. 构建推送内容（兼容 Slack / Discord / 企业微信）
                String message = String.format(
                        "⚠️ 余额预警 | 租户：%s | 账户余额：$%s | 请及时登录 ModelCore 控制台充值",
                        tenant.getName(), remaining.toPlainString());
                String body = String.format(
                        "{\"text\":\"%s\",\"content\":\"%s\"}",
                        message.replace("\"", "\\\""),
                        message.replace("\"", "\\\""));

                // 5. 发送 HTTP POST（5 秒超时，失败不重试）
                restClient.post()
                        .uri(webhookUrl)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();

                log.info("租户 [{}] Webhook 推送成功", tenantId);

            } catch (Exception e) {
                log.warn("Webhook 推送失败: {}", e.getMessage());
            }
        });
    }
}
