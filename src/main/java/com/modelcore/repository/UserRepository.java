package com.modelcore.repository;

import com.modelcore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按用户名查找（登录用） */
    Optional<User> findByUsername(String username);

    /** 检查用户名是否已存在 */
    boolean existsByUsername(String username);

    /** 查询指定租户下的所有用户 */
    List<User> findByTenantId(Long tenantId);

    /** 按租户和状态查询用户 */
    List<User> findByTenantIdAndStatus(Long tenantId, String status);
}
