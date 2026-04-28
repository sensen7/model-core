package com.modelcore.controller;

import com.modelcore.entity.User;
import com.modelcore.security.CustomUserPrincipal;
import com.modelcore.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 团队管理控制器（租户子账号管理）
 * <p>
 * 租户管理员（ADMIN）可创建、禁用/启用本租户下的子账号（MEMBER）。
 * 所有操作基于当前登录用户的 tenantId 进行隔离。
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class TeamController {

    private final UserService userService;

    /** 子账号列表页 */
    @GetMapping
    public String listUsers(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        Long tenantId = principal.getTenantId();
        List<User> users = userService.listUsers(tenantId);
        model.addAttribute("users", users);
        model.addAttribute("username", principal.getUsername());
        return "users";
    }

    /** 创建子账号 */
    @PostMapping("/create")
    public String createUser(@AuthenticationPrincipal CustomUserPrincipal principal,
                             @RequestParam String username,
                             @RequestParam String email,
                             @RequestParam String password,
                             @RequestParam(defaultValue = "MEMBER") String role,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.createUser(principal.getTenantId(), username, email, password, role);
            redirectAttributes.addFlashAttribute("message", "子账号创建成功: " + username);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "创建失败: " + e.getMessage());
        }
        return "redirect:/users";
    }

    /** 禁用子账号 */
    @PostMapping("/{id}/disable")
    public String disableUser(@PathVariable Long id,
                              @AuthenticationPrincipal CustomUserPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        try {
            userService.disableUser(id, principal.getTenantId());
            redirectAttributes.addFlashAttribute("message", "已禁用");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    /** 启用子账号 */
    @PostMapping("/{id}/enable")
    public String enableUser(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserPrincipal principal,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.enableUser(id, principal.getTenantId());
            redirectAttributes.addFlashAttribute("message", "已启用");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }
}
