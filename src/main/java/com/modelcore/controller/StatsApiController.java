package com.modelcore.controller;

import com.modelcore.security.CustomUserPrincipal;
import com.modelcore.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 统计数据 REST 接口
 * <p>
 * 供前端 Chart.js 异步拉取图表数据。
 * 返回近 N 天的每日调用量和费用趋势。
 * </p>
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsApiController {

    private final AuditLogService auditLogService;

    /**
     * 每日调用量趋势
     *
     * @param days 天数（默认 7）
     * @return {labels: ["04-01","04-02",...], data: [10, 25, ...]}
     */
    @GetMapping("/daily-calls")
    public Map<String, Object> dailyCalls(@AuthenticationPrincipal CustomUserPrincipal principal,
                                          @RequestParam(defaultValue = "7") int days) {
        days = Math.max(1, Math.min(days, 365));
        Long tenantId = principal.getTenantId();
        List<Object[]> rawData = auditLogService.getDailyCallCounts(tenantId, days);

        return buildChartData(rawData, days);
    }

    /**
     * 每日费用趋势
     *
     * @param days 天数（默认 7）
     * @return {labels: ["04-01","04-02",...], data: [1.50, 2.30, ...]}
     */
    @GetMapping("/daily-costs")
    public Map<String, Object> dailyCosts(@AuthenticationPrincipal CustomUserPrincipal principal,
                                          @RequestParam(defaultValue = "7") int days) {
        days = Math.max(1, Math.min(days, 365));
        Long tenantId = principal.getTenantId();
        List<Object[]> rawData = auditLogService.getDailyCosts(tenantId, days);

        return buildChartData(rawData, days);
    }

    /**
     * 构建图表数据（补全无数据的日期为 0）
     */
    private Map<String, Object> buildChartData(List<Object[]> rawData, int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        // 将查询结果转为 Map<日期字符串, 数值>
        Map<String, Number> dataMap = new LinkedHashMap<>();
        for (Object[] row : rawData) {
            String dateStr;
            if (row[0] instanceof java.sql.Date sqlDate) {
                dateStr = sqlDate.toLocalDate().format(formatter);
            } else if (row[0] instanceof LocalDate localDate) {
                dateStr = localDate.format(formatter);
            } else {
                // 兜底：安全截取字符串前 10 位
                String s = row[0].toString();
                dateStr = LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s).format(formatter);
            }
            Number value = (Number) row[1];
            dataMap.put(dateStr, value);
        }

        // 补全近 N 天所有日期
        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String dateStr = LocalDate.now().minusDays(i).format(formatter);
            labels.add(dateStr);
            data.add(dataMap.getOrDefault(dateStr, 0));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("data", data);
        return result;
    }
}
