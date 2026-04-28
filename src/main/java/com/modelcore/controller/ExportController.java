package com.modelcore.controller;

import com.modelcore.entity.ApiCallLog;
import com.modelcore.repository.ApiCallLogRepository;
import com.modelcore.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 日志导出控制器
 * <p>
 * 供租户管理员下载本租户的 API 调用日志 CSV 文件。
 * 严格按租户隔离，当前登录用户只能导出自己租户的数据。
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ApiCallLogRepository apiCallLogRepository;

    /** CSV 文件名日期格式 */
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 日期参数解析格式 */
    private static final DateTimeFormatter PARAM_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** CSV 内容时间格式 */
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出 API 调用日志为 CSV 文件
     *
     * @param startDate  开始日期（含），格式 yyyy-MM-dd，默认 30 天前
     * @param endDate    结束日期（含），格式 yyyy-MM-dd，默认今天
     * @param apiKeyId   可选，按指定 API Key 过滤
     * @param principal  当前登录用户（从 Session 中获取）
     * @return CSV 文件响应
     */
    @GetMapping("/logs.csv")
    public ResponseEntity<byte[]> exportLogs(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long apiKeyId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        Long tenantId = principal.getTenantId();

        // 解析日期参数，默认导出最近 30 天
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);

        // 日期范围校验：最多导出 90 天，防止大数据量查询
        if (start.toLocalDate().until(end.toLocalDate(), java.time.temporal.ChronoUnit.DAYS) > 90) {
            start = end.minusDays(90);
            log.warn("租户 [{}] 导出日期范围超过 90 天，已自动截断", tenantId);
        }

        // 查询日志（严格按 tenantId 隔离）
        List<ApiCallLog> logs;
        if (apiKeyId != null) {
            logs = apiCallLogRepository.findByTenantIdAndApiKeyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    tenantId, apiKeyId, start, end);
        } else {
            logs = apiCallLogRepository.findByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    tenantId, start, end);
        }

        // 构建 CSV 内容
        byte[] csvBytes = buildCsv(logs).getBytes(StandardCharsets.UTF_8);

        // 生成文件名：logs_20260420_20260420.csv
        String fileName = String.format("logs_%s_%s.csv",
                start.format(FILE_DATE_FORMAT),
                end.format(FILE_DATE_FORMAT));

        log.info("租户 [{}] 导出日志 {} 条，文件名: {}", tenantId, logs.size(), fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(csvBytes.length))
                .body(csvBytes);
    }

    /**
     * 构建 CSV 文本内容
     * <p>
     * 首行加 BOM（\uFEFF），确保 Excel 直接打开时中文不乱码。
     * </p>
     */
    private String buildCsv(List<ApiCallLog> logs) {
        StringBuilder sb = new StringBuilder();

        // UTF-8 BOM，兼容 Excel 打开中文不乱码
        sb.append('\uFEFF');

        // 表头
        sb.append("时间,API Key ID,模型,供应商,输入Token,输出Token,总Token,费用($),耗时(ms),状态,错误信息\n");

        // 数据行
        for (ApiCallLog log : logs) {
            sb.append(escapeCell(log.getCreatedAt() != null ? log.getCreatedAt().format(DISPLAY_FORMAT) : "")).append(',');
            sb.append(log.getApiKeyId()).append(',');
            sb.append(escapeCell(log.getModel())).append(',');
            sb.append(escapeCell(log.getProvider())).append(',');
            sb.append(log.getPromptTokens()).append(',');
            sb.append(log.getCompletionTokens()).append(',');
            sb.append(log.getTotalTokens()).append(',');
            sb.append(log.getCost() != null ? log.getCost().toPlainString() : "0").append(',');
            sb.append(log.getDuration() != null ? log.getDuration() : 0).append(',');
            sb.append(escapeCell(log.getStatus())).append(',');
            sb.append(escapeCell(log.getErrorMessage())).append('\n');
        }

        return sb.toString();
    }

    /**
     * CSV 单元格转义：若内容含逗号、双引号或换行，则用双引号包裹，内部双引号转义为两个双引号
     */
    private String escapeCell(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 解析开始日期，默认为 30 天前的 00:00:00
     */
    private LocalDateTime parseStartDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now().minusDays(30).atStartOfDay();
        }
        try {
            return LocalDate.parse(dateStr, PARAM_DATE_FORMAT).atStartOfDay();
        } catch (DateTimeParseException e) {
            return LocalDate.now().minusDays(30).atStartOfDay();
        }
    }

    /**
     * 解析结束日期，默认为今天的 23:59:59
     */
    private LocalDateTime parseEndDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now().atTime(LocalTime.MAX);
        }
        try {
            return LocalDate.parse(dateStr, PARAM_DATE_FORMAT).atTime(LocalTime.MAX);
        } catch (DateTimeParseException e) {
            return LocalDate.now().atTime(LocalTime.MAX);
        }
    }
}
