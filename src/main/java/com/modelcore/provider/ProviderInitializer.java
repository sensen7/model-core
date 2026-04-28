package com.modelcore.provider;

import com.modelcore.entity.ProviderConfig;
import com.modelcore.repository.ProviderConfigRepository;
import com.modelcore.service.LoadBalancedChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 供应商初始化与健康检查
 * <p>
 * 1. 应用启动时从数据库加载供应商配置，注册到 LoadBalancedChatService。
 * 2. 定期（每 60 秒）探活上游 API，标记不可用供应商，优化降级速度。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderInitializer implements CommandLineRunner {

    private final ProviderConfigRepository providerConfigRepository;
    private final LoadBalancedChatService loadBalancedChatService;

    @Override
    public void run(String... args) {
        loadProviders();
    }

    /**
     * 从数据库加载活跃供应商配置，按优先级注册
     */
    private void loadProviders() {
        List<ProviderConfig> configs = providerConfigRepository.findByStatusOrderByPriorityAsc("ACTIVE");

        if (configs.isEmpty()) {
            log.warn("数据库中没有活跃的供应商配置，API 转发暂不可用，请登录超管后台添加供应商");
            return;
        }

        for (ProviderConfig config : configs) {
            ProviderClient client = new GenericOpenAICompatibleProvider(config);
            loadBalancedChatService.registerProvider(client);
            log.info("已注册供应商 [{}]，优先级: {}，地址: {}",
                    config.getName(), config.getPriority(), config.getApiUrl());
        }

        log.info("供应商初始化完成，共加载 {} 个供应商", loadBalancedChatService.getProviders().size());
    }

    /**
     * 定期健康检查（每 60 秒）
     * 检查结果同步到 LoadBalancedChatService，不健康的供应商临时移出路由
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void healthCheck() {
        List<ProviderClient> providers = loadBalancedChatService.getProviders();
        for (ProviderClient provider : providers) {
            try {
                boolean healthy = provider.healthCheck();
                loadBalancedChatService.updateProviderStatus(provider.getName(), healthy);
            } catch (Exception e) {
                log.error("供应商 [{}] 健康检查异常：{}", provider.getName(), e.getMessage());
                loadBalancedChatService.updateProviderStatus(provider.getName(), false);
            }
        }
    }
}
