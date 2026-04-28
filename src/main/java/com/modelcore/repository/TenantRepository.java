package com.modelcore.repository;

import com.modelcore.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 租户数据访问层
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /** 按租户名称查找 */
    Optional<Tenant> findByName(String name);

    /** 检查租户名称是否已存在 */
    boolean existsByName(String name);

    /** 按状态查询租户列表（余额同步定时任务用） */
    List<Tenant> findByStatus(String status);
}
