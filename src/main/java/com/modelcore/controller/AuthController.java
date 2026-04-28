package com.modelcore.controller;

import com.modelcore.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 认证控制器
 * <p>
 * 处理登录、注册、登出页面。
 * 登录认证由 Spring Security 表单登录机制自动处理，
 * 此处仅负责页面渲染和注册业务逻辑。
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final TenantService tenantService;

    /** 登录页 */
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("error", "用户名或密码错误");
        }
        if (logout != null) {
            model.addAttribute("message", "已成功退出登录");
        }
        return "login";
    }

    /** 注册页 */
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    /**
     * 注册处理
     * <p>
     * 注册后自动创建租户和默认管理员账号，默认余额为 0。
     * </p>
     */
    @PostMapping("/register")
    public String register(@RequestParam String tenantName,
                           @RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes) {
        try {
            tenantService.register(tenantName, username, email, password);
            redirectAttributes.addFlashAttribute("message", "注册成功，请登录");
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }
}
