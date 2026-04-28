package com.modelcore.controller;

import com.modelcore.entity.ApiCallLog;
import com.modelcore.entity.ApiKey;
import com.modelcore.security.CustomUserPrincipal;
import com.modelcore.service.ApiKeyService;
import com.modelcore.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 调用日志控制器
 * <p>
 * 提供 API 调用日志的分页查询，支持按 API Key 筛选。
 * 数据按租户隔离，仅展示当前租户的日志。
 * </p>
 */
@Controller
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final AuditLogService auditLogService;
    private final ApiKeyService apiKeyService;

    @GetMapping
    public String listLogs(@AuthenticationPrincipal CustomUserPrincipal principal,
                           @RequestParam(required = false) Long apiKeyId,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           Model model) {
        Long tenantId = principal.getTenantId();
        page = Math.max(0, page);
        size = Math.max(1, Math.min(size, 100));

        // 查询日志（支持按 Key 筛选）
        Page<ApiCallLog> logPage;
        if (apiKeyId != null) {
            logPage = auditLogService.getLogsByApiKey(tenantId, apiKeyId, page, size);
        } else {
            logPage = auditLogService.getLogs(tenantId, page, size);
        }

        // 获取 Key 列表（供筛选下拉框使用）
        List<ApiKey> keys = apiKeyService.listKeys(tenantId);

        model.addAttribute("logs", logPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logPage.getTotalPages());
        model.addAttribute("totalElements", logPage.getTotalElements());
        model.addAttribute("selectedKeyId", apiKeyId);
        model.addAttribute("keys", keys);
        model.addAttribute("username", principal.getUsername());

        return "logs";
    }

    /**
     * 单条日志详情（JSON，供弹窗展示请求体和响应体）
     * 严格按租户隔离，只能查看本租户的日志
     */
    @GetMapping("/{id}/detail")
    @ResponseBody
    public ResponseEntity<?> logDetail(@PathVariable Long id,
                                       @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        return auditLogService.getLogById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
