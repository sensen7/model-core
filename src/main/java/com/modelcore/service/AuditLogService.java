package com.modelcore.service;

import com.modelcore.entity.ApiCallLog;
import com.modelcore.repository.ApiCallLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 审计日志服务
 * <p>
 * 提供 API 调用日志的写入和查询功能，用于审计追溯和用量统计。
 * 所有查询均按租户隔离，确保数据安全。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final ApiCallLogRepository apiCallLogRepository;

    /**
     * 记录 API 调用日志
     */
    public ApiCallLog saveLog(ApiCallLog callLog) {
        return apiCallLogRepository.save(callLog);
    }

    /**
     * 按 ID 查询单条日志（含租户隔离校验）
     */
    public Optional<ApiCallLog> getLogById(Long tenantId, Long logId) {
        return apiCallLogRepository.findById(logId)
                .filter(log -> log.getTenantId().equals(tenantId));
    }

    /**
     * 分页查询租户日志
     */
    public Page<ApiCallLog> getLogs(Long tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return apiCallLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    /**
     * 按 API Key 分页查询日志
     */
    public Page<ApiCallLog> getLogsByApiKey(Long tenantId, Long apiKeyId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return apiCallLogRepository.findByTenantIdAndApiKeyIdOrderByCreatedAtDesc(tenantId, apiKeyId, pageable);
    }

    /**
     * 统计今日调用次数
     */
    public long getTodayCallCount(Long tenantId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return apiCallLogRepository.countByTenantIdAndCreatedAtBetween(tenantId, start, end);
    }

    /**
     * 统计今日费用
     */
    public BigDecimal getTodayCost(Long tenantId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return apiCallLogRepository.sumCostByTenantIdAndCreatedAtBetween(tenantId, start, end);
    }

    /**
     * 统计总费用
     */
    public BigDecimal getTotalCost(Long tenantId) {
        return apiCallLogRepository.sumCostByTenantId(tenantId);
    }

    /**
     * 获取近 N 天每日调用量（趋势图数据）
     */
    public List<Object[]> getDailyCallCounts(Long tenantId, int days) {
        LocalDateTime start = LocalDate.now().minusDays(days).atStartOfDay();
        return apiCallLogRepository.countDailyByTenantId(tenantId, start);
    }

    /**
     * 获取近 N 天每日费用（趋势图数据）
     */
    public List<Object[]> getDailyCosts(Long tenantId, int days) {
        LocalDateTime start = LocalDate.now().minusDays(days).atStartOfDay();
        return apiCallLogRepository.sumDailyCostByTenantId(tenantId, start);
    }
}
