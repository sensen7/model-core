package com.modelcore.controller;

import com.modelcore.entity.ApiKey;
import com.modelcore.entity.User;
import com.modelcore.security.CustomUserPrincipal;
import com.modelcore.service.ApiKeyService;
import com.modelcore.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API Key 管理控制器
 * <p>
 * 提供 Key 的列表展示、创建、禁用/启用、充值、设置月额度等后台管理功能。
 * 所有操作均基于当前登录用户的租户 ID 进行隔离。
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    /** Key 列表页 */
    @GetMapping
    public String listKeys(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        Long tenantId = principal.getTenantId();
        List<ApiKey> keys = apiKeyService.listKeys(tenantId);
        List<User> users = userService.listUsers(tenantId);
        // userId -> username 映射，供页面展示关联员工名称
        Map<Long, String> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        model.addAttribute("keys", keys);
        model.addAttribute("users", users);
        model.addAttribute("userMap", userMap);
        model.addAttribute("username", principal.getUsername());
        return "keys";
    }

    /** 创建 API Key */
    @PostMapping("/create")
    public String createKey(@AuthenticationPrincipal CustomUserPrincipal principal,
                            @RequestParam String name,
                            @RequestParam(required = false) Long userId,
                            @RequestParam(required = false) BigDecimal monthlyLimit,
                            @RequestParam(required = false) String expiresAt,
                            RedirectAttributes redirectAttributes) {
        try {
            Long tenantId = principal.getTenantId();
            LocalDateTime expiry = null;
            if (expiresAt != null && !expiresAt.isBlank()) {
                try {
                    expiry = LocalDateTime.parse(expiresAt);
                } catch (DateTimeParseException e) {
                    redirectAttributes.addFlashAttribute("error", "过期时间格式错误，请使用 yyyy-MM-ddTHH:mm 格式");
                    return "redirect:/keys";
                }
            }
            ApiKey apiKey = apiKeyService.createKey(tenantId, userId, name, monthlyLimit, expiry);
            redirectAttributes.addFlashAttribute("message", "创建成功，Key: " + apiKey.getKeyValue());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "创建失败: " + e.getMessage());
        }
        return "redirect:/keys";
    }

    /** 禁用 API Key */
    @PostMapping("/{id}/disable")
    public String disableKey(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserPrincipal principal,
                             RedirectAttributes redirectAttributes) {
        try {
            apiKeyService.disableKey(id, principal.getTenantId());
            redirectAttributes.addFlashAttribute("message", "已禁用");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/keys";
    }

    /** 启用 API Key */
    @PostMapping("/{id}/enable")
    public String enableKey(@PathVariable Long id,
                            @AuthenticationPrincipal CustomUserPrincipal principal,
                            RedirectAttributes redirectAttributes) {
        try {
            apiKeyService.enableKey(id, principal.getTenantId());
            redirectAttributes.addFlashAttribute("message", "已启用");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/keys";
    }

    /** 更新月额度 */
    @PostMapping("/{id}/limit")
    public String updateLimit(@PathVariable Long id,
                              @RequestParam(required = false) BigDecimal monthlyLimit,
                              @AuthenticationPrincipal CustomUserPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        try {
            apiKeyService.updateMonthlyLimit(id, principal.getTenantId(), monthlyLimit);
            redirectAttributes.addFlashAttribute("message", "额度已更新");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/keys";
    }
}
