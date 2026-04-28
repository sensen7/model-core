package com.modelcore.controller;

import com.modelcore.entity.Tenant;
import com.modelcore.security.CustomUserPrincipal;
import com.modelcore.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

/**
 * 平台超级管理员控制器
 * <p>
 * 仅 SUPER_ADMIN 角色可访问，提供租户列表、停用/启用、充值等全平台管理功能。
 * 路径统一以 /admin 开头，SecurityConfig 中已配置 ROLE_SUPER_ADMIN 权限校验。
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TenantService tenantService;

    /** 租户列表页 */
    @GetMapping("/tenants")
    public String listTenants(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        List<Tenant> tenants = tenantService.listAllTenants();
        model.addAttribute("tenants", tenants);
        model.addAttribute("username", principal.getUsername());
        return "admin/tenants";
    }

    /** 停用租户 */
    @PostMapping("/tenants/{id}/suspend")
    public String suspendTenant(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tenantService.updateStatus(id, "SUSPENDED");
            redirectAttributes.addFlashAttribute("message", "租户已停用");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tenants";
    }

    /** 启用租户 */
    @PostMapping("/tenants/{id}/activate")
    public String activateTenant(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tenantService.updateStatus(id, "ACTIVE");
            redirectAttributes.addFlashAttribute("message", "租户已启用");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tenants";
    }

    /** 给租户充值 */
    @PostMapping("/tenants/{id}/recharge")
    public String rechargeTenant(@PathVariable Long id,
                                 @RequestParam BigDecimal amount,
                                 RedirectAttributes redirectAttributes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "充值金额必须大于 0");
            return "redirect:/admin/tenants";
        }
        try {
            tenantService.rechargeTenant(id, amount);
            redirectAttributes.addFlashAttribute("message", "充值成功: $" + amount);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tenants";
    }
}
