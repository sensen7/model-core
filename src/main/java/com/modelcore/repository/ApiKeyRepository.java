package com.modelcore.repository;

import com.modelcore.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * API Key 数据访问层
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** 按 Key 值查找（API 调用认证用） */
    Optional<ApiKey> findByKeyValue(String keyValue);

    /** 查询指定租户下的所有 API Key */
    List<ApiKey> findByTenantId(Long tenantId);

    /** 按租户和状态查询 API Key */
    List<ApiKey> findByTenantIdAndStatus(Long tenantId, String status);

    /** 统计指定租户下活跃 Key 数量 */
    long countByTenantIdAndStatus(Long tenantId, String status);

    /** 检查 Key 值是否已存在 */
    boolean existsByKeyValue(String keyValue);

    /** 查询指定员工的所有 API Key */
    List<ApiKey> findByUserId(Long userId);

    /** 查询指定租户下指定员工的所有 API Key */
    List<ApiKey> findByTenantIdAndUserId(Long tenantId, Long userId);

    /** 按状态查询所有 API Key（跨租户，用于余额同步定时任务） */
    List<ApiKey> findByStatus(String status);
}
