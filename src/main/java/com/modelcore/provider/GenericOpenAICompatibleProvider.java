package com.modelcore.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelcore.entity.ProviderConfig;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 通用 OpenAI 兼容供应商适配器
 * <p>
 * 只要上游服务支持 OpenAI 格式的 /v1/chat/completions 接口，
 * 填写 API URL 和 Key 即可接入，无需编写专属代码。
 * 基于 JDK 21 虚拟线程，使用同步阻塞调用，代码简洁易读。
 * WebClient 仅作为 HTTP 客户端使用，通过 .block() 转为同步。
 * </p>
 */
@Slf4j
public class GenericOpenAICompatibleProvider implements ProviderClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProviderConfig config;
    private final WebClient webClient;
    /** 模型名称映射表（如将 "gpt-4" 映射为 "deepseek-chat"） */
    private final Map<String, String> modelMapping;

    public GenericOpenAICompatibleProvider(ProviderConfig config) {
        this.config = config;
        this.modelMapping = parseModelMapping(config.getModelMapping());

        // 构建 WebClient，baseUrl 为供应商 API 地址，超时从配置读取
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getTimeout())
                .responseTimeout(Duration.ofMillis(config.getTimeout()));

        this.webClient = WebClient.builder()
                .baseUrl(config.getApiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public int getPriority() {
        return config.getPriority();
    }

    @Override
    public BigDecimal getInputPricePerToken() {
        return config.getInputPricePerToken();
    }

    @Override
    public BigDecimal getOutputPricePerToken() {
        return config.getOutputPricePerToken();
    }

    /**
     * 流式调用（SSE）
     * 在虚拟线程中同步读取上游 SSE 流，逐行通过 onData 回调推送。
     * 虚拟线程阻塞时不占用平台线程，并发能力与响应式等价。
     */
    @Override
    public void streamChat(Map<String, Object> requestBody,
                           Consumer<String> onData,
                           Runnable onComplete,
                           Consumer<Throwable> onError) {
        Map<String, Object> mappedBody = applyModelMapping(requestBody);
        try {
            webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(mappedBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class).flatMap(body ->
                                    Mono.error(new RuntimeException("供应商 [" + config.getName() + "] 返回错误 "
                                            + resp.statusCode().value() + ": " + body)))
                    )
                    // 逐行读取 SSE 响应（每行为一个 data: ... 片段）
                    .bodyToFlux(String.class)
                    .doOnNext(onData)
                    .doOnComplete(onComplete)
                    .doOnError(onError)
                    // block() 在虚拟线程中执行，不会阻塞平台线程
                    .blockLast();
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    /**
     * 非流式调用（同步阻塞）
     */
    @Override
    public String chat(Map<String, Object> requestBody) {
        Map<String, Object> mappedBody = applyModelMapping(requestBody);
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(mappedBody)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RuntimeException("供应商 [" + config.getName() + "] 返回错误 "
                                        + resp.statusCode().value() + ": " + body)))
                )
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 向量嵌入调用（同步阻塞）
     */
    @Override
    public String embeddings(Map<String, Object> requestBody) {
        Map<String, Object> mappedBody = applyModelMapping(requestBody);
        return webClient.post()
                .uri("/v1/embeddings")
                .bodyValue(mappedBody)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RuntimeException("供应商 [" + config.getName() + "] Embeddings 返回错误 "
                                        + resp.statusCode().value() + ": " + body)))
                )
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 健康检查（同步阻塞）
     * 请求 /v1/models 判断供应商是否可达：
     * - 2xx：健康
     * - 401/403/429：Key 问题或限流，供应商本身正常，视为健康
     * - 5xx / 超时 / 连接失败：供应商故障，视为不健康
     */
    @Override
    public boolean healthCheck() {
        try {
            Boolean result = webClient.get()
                    .uri("/v1/models")
                    .exchangeToMono(resp -> {
                        int code = resp.statusCode().value();
                        // Key 鉴权问题或限流，不代表供应商故障
                        if (code == 401 || code == 403 || code == 429) {
                            log.debug("供应商 [{}] /v1/models 返回 {}（Key 问题），仍视为健康", config.getName(), code);
                            return resp.releaseBody().thenReturn(true);
                        }
                        return resp.releaseBody().thenReturn(resp.statusCode().is2xxSuccessful());
                    })
                    .timeout(Duration.ofSeconds(5))
                    .onErrorReturn(false)
                    .block();
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("供应商 [{}] 健康检查异常: {}", config.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 应用模型名称映射
     * 例如客户端请求 "gpt-4"，实际转发时替换为 "deepseek-chat"
     */
    private Map<String, Object> applyModelMapping(Map<String, Object> requestBody) {
        if (modelMapping.isEmpty()) {
            return requestBody;
        }
        String model = (String) requestBody.get("model");
        if (model == null || !modelMapping.containsKey(model)) {
            return requestBody;
        }
        Map<String, Object> mapped = new HashMap<>(requestBody);
        mapped.put("model", modelMapping.get(model));
        log.debug("供应商 [{}] 模型映射: {} -> {}", config.getName(), model, mapped.get("model"));
        return mapped;
    }

    /**
     * 解析 JSON 格式的模型映射配置
     * 配置示例：{"gpt-4":"deepseek-chat","gpt-3.5-turbo":"deepseek-chat"}
     */
    private Map<String, String> parseModelMapping(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("供应商 [{}] 模型映射配置解析失败，将忽略映射: {}", config.getName(), e.getMessage());
            return Collections.emptyMap();
        }
    }
}
