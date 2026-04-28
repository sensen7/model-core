package com.modelcore.repository;

import com.modelcore.entity.ProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 上游供应商配置数据访问层
 */
@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, Long> {

    /** 按状态查询并按优先级排序（数值越小优先级越高） */
    List<ProviderConfig> findByStatusOrderByPriorityAsc(String status);

    /** 按名称查找供应商 */
    List<ProviderConfig> findByName(String name);
}
