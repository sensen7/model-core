package com.modelcore.controller;

import com.modelcore.entity.ProviderConfig;
import com.modelcore.provider.GenericOpenAICompatibleProvider;
import com.modelcore.provider.ProviderClient;
import com.modelcore.repository.ProviderConfigRepository;
import com.modelcore.security.CustomUserPrincipal;
import com.modelcore.service.LoadBalancedChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 平台超管专用：AI 供应商管理控制器
 * <p>
 * 管理上游 AI 服务供应商的连接配置（增删改查 + 实时路由更新）。
 * 创建/启用供应商后立即注册到路由，无需重启服务。
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/admin/providers")
@RequiredArgsConstructor
public class AdminProviderController {

    private final ProviderConfigRepository providerConfigRepository;
    private final LoadBalancedChatService loadBalancedChatService;

    /** 供应商列表页 */
    @GetMapping
    public String listProviders(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        List<ProviderConfig> providers = providerConfigRepository.findAll();
        // 查询当前不健康的供应商名称集合，传给页面渲染健康状态列
        Set<String> unhealthyNames = loadBalancedChatService.getUnhealthyProviderNames();
        model.addAttribute("providers", providers);
        model.addAttribute("unhealthyNames", unhealthyNames);
        model.addAttribute("username", principal.getUsername());
        return "admin/providers";
    }

    /**
     * 供应商健康状态查询接口（JSON，供 admin 页面定时刷新使用）
     * 返回每个供应商的名称、配置状态、健康状态
     * 注意：租户页面的健康刷新请使用 /api/providers/health，此接口仅限超管
     */
    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getProvidersHealth() {
        List<ProviderConfig> providers = providerConfigRepository.findAll();
        Set<String> unhealthyNames = loadBalancedChatService.getUnhealthyProviderNames();
        List<Map<String, Object>> result = providers.stream()
                .map(p -> Map.<String, Object>of(
                        "name", p.getName(),
                        "configStatus", p.getStatus(),
                        // ACTIVE 且不在不健康集合中，才算真正健康
                        "healthy", "ACTIVE".equals(p.getStatus()) && !unhealthyNames.contains(p.getName())
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** 创建供应商（保存到数据库并立即注册到路由） */
    @PostMapping("/create")
    public String createProvider(@RequestParam String name,
                                 @RequestParam String apiUrl,
                                 @RequestParam String apiKey,
                                 @RequestParam(defaultValue = "1") Integer priority,
                                 @RequestParam(defaultValue = "10000") Integer timeout,
                                 @RequestParam(defaultValue = "1.0") BigDecimal inputPricePerMillion,
                                 @RequestParam(defaultValue = "2.0") BigDecimal outputPricePerMillion,
                                 @RequestParam(required = false) String modelMapping,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (name == null || name.isBlank() || apiUrl == null || apiUrl.isBlank()
                    || apiKey == null || apiKey.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "名称、API URL 和 API Key 不能为空");
                return "redirect:/admin/providers";
            }

            ProviderConfig config = ProviderConfig.builder()
                    .name(name.trim())
                    .apiUrl(apiUrl.trim())
                    .apiKey(apiKey.trim())
                    .priority(priority)
                    .timeout(timeout)
                    .status("ACTIVE")
                    .inputPricePerMillion(inputPricePerMillion)
                    .outputPricePerMillion(outputPricePerMillion)
                    .modelMapping(modelMapping != null && !modelMapping.isBlank() ? modelMapping.trim() : null)
                    .build();

            providerConfigRepository.save(config);

            // 立即注册到路由（无需重启）
            ProviderClient client = new GenericOpenAICompatibleProvider(config);
            loadBalancedChatService.registerProvider(client);

            log.info("超管新建供应商 [{}] 并注册到路由", name);
            redirectAttributes.addFlashAttribute("message", "供应商 [" + name + "] 创建成功并已加入路由");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "创建失败: " + e.getMessage());
        }
        return "redirect:/admin/providers";
    }

    /** 启用供应商（更新状态并重新注册到路由） */
    @PostMapping("/{id}/activate")
    public String activateProvider(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ProviderConfig config = providerConfigRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("供应商不存在"));
            config.setStatus("ACTIVE");
            providerConfigRepository.save(config);

            // 重新注册到路由
            ProviderClient client = new GenericOpenAICompatibleProvider(config);
            loadBalancedChatService.registerProvider(client);

            log.info("超管启用供应商 [{}] 并重新注册到路由", config.getName());
            redirectAttributes.addFlashAttribute("message", "供应商 [" + config.getName() + "] 已启用");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/providers";
    }

    /** 停用供应商（更新状态并从路由移除） */
    @PostMapping("/{id}/deactivate")
    public String deactivateProvider(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ProviderConfig config = providerConfigRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("供应商不存在"));
            config.setStatus("INACTIVE");
            providerConfigRepository.save(config);

            // 从路由中移除
            loadBalancedChatService.removeProvider(config.getName());

            log.info("超管停用供应商 [{}] 并从路由移除", config.getName());
            redirectAttributes.addFlashAttribute("message", "供应商 [" + config.getName() + "] 已停用");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/providers";
    }

    /** 编辑供应商（修改配置并立即重新注册到路由） */
    @PostMapping("/{id}/update")
    public String updateProvider(@PathVariable Long id,
                                 @RequestParam String name,
                                 @RequestParam String apiUrl,
                                 @RequestParam(required = false) String apiKey,
                                 @RequestParam(defaultValue = "1") Integer priority,
                                 @RequestParam(defaultValue = "10000") Integer timeout,
                                 @RequestParam(defaultValue = "1.0") BigDecimal inputPricePerMillion,
                                 @RequestParam(defaultValue = "2.0") BigDecimal outputPricePerMillion,
                                 @RequestParam(required = false) String modelMapping,
                                 RedirectAttributes redirectAttributes) {
        try {
            ProviderConfig config = providerConfigRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("供应商不存在"));

            if (name == null || name.isBlank() || apiUrl == null || apiUrl.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "名称和 API URL 不能为空");
                return "redirect:/admin/providers";
            }

            config.setName(name.trim());
            config.setApiUrl(apiUrl.trim());
            // API Key 留空表示不修改
            if (apiKey != null && !apiKey.isBlank()) {
                config.setApiKey(apiKey.trim());
            }
            config.setPriority(priority);
            config.setTimeout(timeout);
            config.setInputPricePerMillion(inputPricePerMillion);
            config.setOutputPricePerMillion(outputPricePerMillion);
            config.setModelMapping(modelMapping != null && !modelMapping.isBlank() ? modelMapping.trim() : null);
            providerConfigRepository.save(config);

            // 若供应商处于 ACTIVE 状态，立即用新配置重新注册到路由
            if ("ACTIVE".equals(config.getStatus())) {
                ProviderClient client = new GenericOpenAICompatibleProvider(config);
                loadBalancedChatService.registerProvider(client);
                log.info("超管更新供应商 [{}] 并重新注册到路由", config.getName());
            }

            redirectAttributes.addFlashAttribute("message", "供应商 [" + config.getName() + "] 已更新");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "更新失败: " + e.getMessage());
        }
        return "redirect:/admin/providers";
    }

    /** 删除供应商（仅允许删除 INACTIVE 状态的供应商） */
    @PostMapping("/{id}/delete")
    public String deleteProvider(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ProviderConfig config = providerConfigRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("供应商不存在"));

            if ("ACTIVE".equals(config.getStatus())) {
                redirectAttributes.addFlashAttribute("error", "请先停用供应商再删除");
                return "redirect:/admin/providers";
            }

            providerConfigRepository.deleteById(id);
            log.info("超管删除供应商 [{}]", config.getName());
            redirectAttributes.addFlashAttribute("message", "供应商 [" + config.getName() + "] 已删除");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/providers";
    }
}
