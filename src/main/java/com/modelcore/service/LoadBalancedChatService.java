package com.modelcore.service;

import com.modelcore.provider.ProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 负载均衡聊天服务（智能降级 + 多实例共享健康状态）
 * <p>
 * 核心路由逻辑：按优先级依次尝试供应商，当主路由超时或返回错误时，
 * 自动切换到备用路由，确保请求成功。
 * 基于 JDK 21 虚拟线程，使用同步 StringRedisTemplate。
 * 健康状态存储在 Redis Set（key: modelcore:unhealthy_providers），多实例共享，
 * 每条记录设置 120 秒 TTL，防止 Redis 宕机后供应商被永久屏蔽。
 * </p>
 */
@Slf4j
@Service
public class LoadBalancedChatService {

    /** Redis 中存储不健康供应商名称的 Set key */
    private static final String UNHEALTHY_PROVIDERS_KEY = "modelcore:unhealthy_providers";
    /** 不健康状态 TTL：120 秒后自动解除 */
    private static final Duration UNHEALTHY_TTL = Duration.ofSeconds(120);

    private final List<ProviderClient> providers = new CopyOnWriteArrayList<>();
    private final StringRedisTemplate redisTemplate;

    public LoadBalancedChatService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 注册供应商客户端（先移除同名旧实例，再添加，按优先级排序）
     */
    public void registerProvider(ProviderClient provider) {
        removeProvider(provider.getName());
        providers.add(provider);
        providers.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        log.info("注册供应商: {} (优先级: {}, 当前共 {} 个)", provider.getName(), provider.getPriority(), providers.size());
    }

    /**
     * 从路由中移除指定供应商（停用时调用）
     */
    public void removeProvider(String name) {
        providers.removeIf(p -> p.getName().equals(name));
        redisTemplate.opsForSet().remove(UNHEALTHY_PROVIDERS_KEY, name);
        log.info("供应商 [{}] 已从路由中移除", name);
    }

    /**
     * 获取已注册的供应商列表
     */
    public List<ProviderClient> getProviders() {
        return List.copyOf(providers);
    }

    /**
     * 按名称查找供应商实例（用于获取动态价格）
     */
    public ProviderClient getProvider(String name) {
        return providers.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * 更新供应商健康状态，写入 Redis Set
     */
    public void updateProviderStatus(String name, boolean healthy) {
        if (healthy) {
            Long removed = redisTemplate.opsForSet().remove(UNHEALTHY_PROVIDERS_KEY, name);
            if (removed != null && removed > 0) {
                log.info("供应商 [{}] 健康检查恢复正常，重新加入路由", name);
            }
        } else {
            redisTemplate.opsForSet().add(UNHEALTHY_PROVIDERS_KEY, name);
            redisTemplate.expire(UNHEALTHY_PROVIDERS_KEY, UNHEALTHY_TTL);
            log.warn("供应商 [{}] 健康检查失败，临时移出路由（TTL={}s）", name, UNHEALTHY_TTL.getSeconds());
        }
    }

    /**
     * 获取当前不健康的供应商名称集合
     */
    public Set<String> getUnhealthyProviderNames() {
        Set<String> members = redisTemplate.opsForSet().members(UNHEALTHY_PROVIDERS_KEY);
        return members != null ? members : Set.of();
    }

    /**
     * 获取可用供应商列表：健康的排在前面，不健康的排在后面作为兜底。
     * 健康检查只影响路由优先级，不会完全屏蔽任何供应商，
     * 避免健康检查误判导致所有供应商不可用。
     */
    private List<ProviderClient> getAvailableProviders() {
        Set<String> unhealthy = getUnhealthyProviderNames();
        if (unhealthy.isEmpty()) {
            return new ArrayList<>(providers);
        }
        // 健康的排前面，不健康的排后面，保证至少有供应商可用
        List<ProviderClient> healthy = providers.stream()
                .filter(p -> !unhealthy.contains(p.getName()))
                .collect(Collectors.toList());
        List<ProviderClient> fallback = providers.stream()
                .filter(p -> unhealthy.contains(p.getName()))
                .collect(Collectors.toList());
        List<ProviderClient> result = new ArrayList<>(healthy);
        result.addAll(fallback);
        if (healthy.isEmpty()) {
            log.warn("所有供应商均被标记为不健康，将尝试全部供应商");
        }
        return result;
    }

    /**
     * 流式聊天请求（带自动降级）
     * 在虚拟线程中同步执行，按优先级依次尝试供应商。
     *
     * @param requestBody  请求体
     * @param providerName 输出参数，记录实际使用的供应商名称
     * @param onData       收到每行 SSE 数据时的回调
     * @param onComplete   流正常结束时的回调
     * @param onError      所有供应商均失败时的回调
     */
    public void streamChat(Map<String, Object> requestBody, AtomicReference<String> providerName,
                           Consumer<String> onData, Runnable onComplete, Consumer<Throwable> onError) {
        if (providers.isEmpty()) {
            onError.accept(new RuntimeException("没有可用的供应商"));
            return;
        }
        List<ProviderClient> available = getAvailableProviders();
        tryStreamWithFallback(available, 0, requestBody, providerName, onData, onComplete, onError);
    }

    /**
     * 递归尝试供应商降级（流式）
     */
    private void tryStreamWithFallback(List<ProviderClient> available, int index,
                                        Map<String, Object> requestBody,
                                        AtomicReference<String> providerName,
                                        Consumer<String> onData, Runnable onComplete,
                                        Consumer<Throwable> onError) {
        if (index >= available.size()) {
            onError.accept(new RuntimeException("所有供应商均不可用"));
            return;
        }
        ProviderClient provider = available.get(index);
        log.info("尝试供应商（流式）: {}", provider.getName());
        providerName.set(provider.getName());

        provider.streamChat(requestBody, onData, onComplete, e -> {
            log.warn("供应商 [{}] 流式失败，降级到下一个: {}", provider.getName(), e.getMessage());
            tryStreamWithFallback(available, index + 1, requestBody, providerName, onData, onComplete, onError);
        });
    }

    /**
     * 非流式聊天请求（带自动降级）
     *
     * @param requestBody  请求体
     * @param providerName 输出参数，记录实际使用的供应商名称
     * @return 完整 JSON 响应
     */
    public String chat(Map<String, Object> requestBody, AtomicReference<String> providerName) {
        if (providers.isEmpty()) {
            throw new RuntimeException("没有可用的供应商");
        }
        List<ProviderClient> available = getAvailableProviders();
        Exception lastError = null;
        for (ProviderClient provider : available) {
            try {
                log.info("尝试供应商（非流式）: {}", provider.getName());
                providerName.set(provider.getName());
                return provider.chat(requestBody);
            } catch (Exception e) {
                log.warn("供应商 [{}] 失败，降级到下一个: {}", provider.getName(), e.getMessage());
                lastError = e;
            }
        }
        throw new RuntimeException("所有供应商均不可用", lastError);
    }

    /**
     * 向量嵌入请求（带自动降级）
     *
     * @param requestBody  请求体
     * @param providerName 输出参数，记录实际使用的供应商名称
     * @return 完整 JSON 响应
     */
    public String embeddings(Map<String, Object> requestBody, AtomicReference<String> providerName) {
        if (providers.isEmpty()) {
            throw new RuntimeException("没有可用的供应商");
        }
        List<ProviderClient> available = getAvailableProviders();
        Exception lastError = null;
        for (ProviderClient provider : available) {
            try {
                log.info("Embeddings 尝试供应商: {}", provider.getName());
                providerName.set(provider.getName());
                return provider.embeddings(requestBody);
            } catch (Exception e) {
                log.warn("供应商 [{}] Embeddings 失败，降级到下一个: {}", provider.getName(), e.getMessage());
                lastError = e;
            }
        }
        throw new RuntimeException("所有供应商均不可用", lastError);
    }
}
