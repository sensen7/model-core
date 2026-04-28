package com.modelcore.controller;

import com.modelcore.entity.ProviderConfig;
import com.modelcore.repository.ProviderConfigRepository;
import com.modelcore.service.LoadBalancedChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商健康状态查询接口（租户可访问）
 * <p>
 * 仅返回供应商名称和健康状态，不暴露 API Key、URL 等敏感信息。
 * 供租户"可用模型"页面定时刷新健康状态徽章使用。
 * </p>
 */
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderStatusController {

    private final ProviderConfigRepository providerConfigRepository;
    private final LoadBalancedChatService loadBalancedChatService;

    /**
     * 返回所有活跃供应商的健康状态
     * 仅包含名称和健康状态，不含敏感字段
     */
    @GetMapping("/health")
    public ResponseEntity<List<Map<String, Object>>> getHealth() {
        List<ProviderConfig> activeProviders = providerConfigRepository.findByStatusOrderByPriorityAsc("ACTIVE");
        Set<String> unhealthyNames = loadBalancedChatService.getUnhealthyProviderNames();
        List<Map<String, Object>> result = activeProviders.stream()
                .map(p -> Map.<String, Object>of(
                        "name", p.getName(),
                        "healthy", !unhealthyNames.contains(p.getName())
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
