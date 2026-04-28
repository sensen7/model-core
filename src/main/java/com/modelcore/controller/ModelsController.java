package com.modelcore.controller;

import com.modelcore.entity.ProviderConfig;
import com.modelcore.repository.ProviderConfigRepository;
import com.modelcore.security.CustomUserPrincipal;
import com.modelcore.service.LoadBalancedChatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

/**
 * 租户可用模型列表页
 * <p>
 * 展示当前系统中所有活跃供应商及其支持的模型列表，
 * 供租户了解可调用的模型名称，不暴露 API Key 和 URL 等敏感信息。
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ModelsController {

    private final ProviderConfigRepository providerConfigRepository;
    private final LoadBalancedChatService loadBalancedChatService;
    private final ObjectMapper objectMapper;

    /**
     * 租户模型列表页
     * 按供应商分组展示可用模型，标注健康状态
     */
    @GetMapping("/models")
    public String modelsPage(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        // 只展示 ACTIVE 状态的供应商
        List<ProviderConfig> activeProviders = providerConfigRepository.findByStatusOrderByPriorityAsc("ACTIVE");
        // 获取当前不健康的供应商名称集合
        Set<String> unhealthyNames = loadBalancedChatService.getUnhealthyProviderNames();

        // 组装每个供应商的视图数据（屏蔽敏感字段）
        List<Map<String, Object>> providerViews = new ArrayList<>();
        for (ProviderConfig config : activeProviders) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", config.getName());
            view.put("priority", config.getPriority());
            view.put("inputPricePerMillion", config.getInputPricePerMillion());
            view.put("outputPricePerMillion", config.getOutputPricePerMillion());
            view.put("healthy", !unhealthyNames.contains(config.getName()));
            // 从 modelMapping JSON 解析出可用的模型名称列表（key 集合即为下游可用的模型名）
            view.put("models", parseModelNames(config.getModelMapping()));
            providerViews.add(view);
        }

        model.addAttribute("providerViews", providerViews);
        model.addAttribute("username", principal.getUsername());
        return "models";
    }

    /**
     * 从 modelMapping JSON 中解析出可用的模型名称列表
     * 若未配置映射，返回空列表（表示透传，用户可传任意模型名）
     *
     * @param modelMappingJson 供应商配置的 modelMapping JSON 字符串
     * @return 可用模型名称列表（即映射表的 key 集合）
     */
    private List<String> parseModelNames(String modelMappingJson) {
        if (modelMappingJson == null || modelMappingJson.isBlank()) {
            return List.of();
        }
        try {
            Map<String, String> mapping = objectMapper.readValue(
                    modelMappingJson, new TypeReference<Map<String, String>>() {});
            return new ArrayList<>(mapping.keySet());
        } catch (Exception e) {
            log.warn("解析 modelMapping JSON 失败：{}", e.getMessage());
            return List.of();
        }
    }
}
