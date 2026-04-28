package com.modelcore.repository;

import com.modelcore.entity.ApiCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API 调用日志数据访问层
 */
@Repository
public interface ApiCallLogRepository extends JpaRepository<ApiCallLog, Long> {

    /** 按租户分页查询日志（按时间倒序） */
    Page<ApiCallLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    /** 按租户和 API Key 分页查询日志 */
    Page<ApiCallLog> findByTenantIdAndApiKeyIdOrderByCreatedAtDesc(Long tenantId, Long apiKeyId, Pageable pageable);

    /** 统计指定租户在时间范围内的调用次数 */
    @Query("SELECT COUNT(l) FROM ApiCallLog l WHERE l.tenantId = :tenantId AND l.createdAt >= :start AND l.createdAt < :end")
    long countByTenantIdAndCreatedAtBetween(@Param("tenantId") Long tenantId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /** 统计指定租户在时间范围内的总费用 */
    @Query("SELECT COALESCE(SUM(l.cost), 0) FROM ApiCallLog l WHERE l.tenantId = :tenantId AND l.createdAt >= :start AND l.createdAt < :end")
    BigDecimal sumCostByTenantIdAndCreatedAtBetween(@Param("tenantId") Long tenantId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    /** 统计指定租户的全部总费用 */
    @Query("SELECT COALESCE(SUM(l.cost), 0) FROM ApiCallLog l WHERE l.tenantId = :tenantId")
    BigDecimal sumCostByTenantId(@Param("tenantId") Long tenantId);

    /** 按天统计调用量（近 N 天趋势图用） */
    @Query("SELECT CAST(l.createdAt AS DATE), COUNT(l) FROM ApiCallLog l WHERE l.tenantId = :tenantId AND l.createdAt >= :start GROUP BY CAST(l.createdAt AS DATE) ORDER BY CAST(l.createdAt AS DATE)")
    List<Object[]> countDailyByTenantId(@Param("tenantId") Long tenantId,
                                        @Param("start") LocalDateTime start);

    /** 按天统计费用（近 N 天趋势图用） */
    @Query("SELECT CAST(l.createdAt AS DATE), COALESCE(SUM(l.cost), 0) FROM ApiCallLog l WHERE l.tenantId = :tenantId AND l.createdAt >= :start GROUP BY CAST(l.createdAt AS DATE) ORDER BY CAST(l.createdAt AS DATE)")
    List<Object[]> sumDailyCostByTenantId(@Param("tenantId") Long tenantId,
                                          @Param("start") LocalDateTime start);

    /** 导出日志：按租户+时间范围查询全部记录（不分页，用于 CSV 导出） */
    List<ApiCallLog> findByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long tenantId, LocalDateTime start, LocalDateTime end);

    /** 导出日志：按租户+API Key+时间范围查询（不分页，用于 CSV 导出） */
    List<ApiCallLog> findByTenantIdAndApiKeyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long tenantId, Long apiKeyId, LocalDateTime start, LocalDateTime end);
}
