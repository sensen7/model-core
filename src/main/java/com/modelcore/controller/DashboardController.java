package com.modelcore.controller;

import com.modelcore.security.CustomUserPrincipal;
import com.modelcore.service.ApiKeyService;
import com.modelcore.service.AuditLogService;
import com.modelcore.service.TokenDeductionService;
import com.modelcore.entity.Tenant;
import com.modelcore.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/**
 * 仪表盘控制器
 * <p>
 * 展示当前租户的核心运营指标：今日调用量、总费用、活跃 Key 数。
 * 趋势图数据通过 StatsApiController 异步加载。
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final AuditLogService auditLogService;
    private final ApiKeyService apiKeyService;
    private final TenantRepository tenantRepository;
    private final TokenDeductionService tokenDeductionService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        Long tenantId = principal.getTenantId();

        // 今日调用量
        long todayCalls = auditLogService.getTodayCallCount(tenantId);
        // 今日费用
        BigDecimal todayCost = auditLogService.getTodayCost(tenantId);
        // 总费用
        BigDecimal totalCost = auditLogService.getTotalCost(tenantId);
        // 活跃 Key 数
        long activeKeys = apiKeyService.countActiveKeys(tenantId);

        model.addAttribute("todayCalls", todayCalls);
        model.addAttribute("todayCost", todayCost);
        model.addAttribute("totalCost", totalCost);
        model.addAttribute("activeKeys", activeKeys);
        model.addAttribute("username", principal.getUsername());

        // 从 Redis 读取租户实时余额（Redis 无值时回退读 DB）
        BigDecimal tenantBalance = tokenDeductionService.getTenantBalance(tenantId);
        if (tenantBalance == null || tenantBalance.compareTo(BigDecimal.ZERO) == 0) {
            tenantBalance = tenantRepository.findById(tenantId)
                    .map(Tenant::getBalance)
                    .orElse(BigDecimal.ZERO);
        }
        model.addAttribute("tenantBalance", tenantBalance);

        // 注入当前租户的 Webhook URL（用于设置表单回显）
        tenantRepository.findById(tenantId).ifPresent(t ->
                model.addAttribute("webhookUrl", t.getWebhookUrl()));

        return "dashboard";
    }

    /**
     * 保存租户的 Webhook URL 设置
     *
     * @param webhookUrl Webhook 地址（可为空，表示清除）
     */
    @PostMapping("/dashboard/webhook")
    public String saveWebhook(@RequestParam(required = false) String webhookUrl,
                              @AuthenticationPrincipal CustomUserPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        Long tenantId = principal.getTenantId();
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            // 空字符串视为清除
            tenant.setWebhookUrl(webhookUrl != null && !webhookUrl.isBlank() ? webhookUrl.trim() : null);
            tenantRepository.save(tenant);
        });
        redirectAttributes.addFlashAttribute("webhookSuccess", "Webhook 设置已保存");
        return "redirect:/dashboard";
    }
}
