-- =============================================================
-- ModelCore 数据库初始化脚本（MariaDB 10.6+ 语法）
-- =============================================================

CREATE DATABASE IF NOT EXISTS model_core
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE model_core;

-- -----------------------------------------------------------
-- 1. 租户表（企业客户）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenant (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100)   NOT NULL COMMENT '租户名称（企业名）',
    contact_email   VARCHAR(200)            COMMENT '联系邮箱',
    balance         DECIMAL(16,4)  NOT NULL DEFAULT 0.0000 COMMENT '账户余额（美元）',
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/SUSPENDED',
    webhook_url     VARCHAR(500)            COMMENT '余额预警 Webhook 地址（兼容 Slack/Discord/企业微信）',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- -----------------------------------------------------------
-- 2. 用户表（租户下的子账号）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT                  COMMENT '所属租户 ID（超管为 NULL）',
    username        VARCHAR(50)    NOT NULL COMMENT '用户名（全局唯一）',
    email           VARCHAR(200)            COMMENT '邮箱',
    password_hash   VARCHAR(200)   NOT NULL COMMENT '密码哈希（BCrypt）',
    role            VARCHAR(20)    NOT NULL DEFAULT 'ADMIN' COMMENT '角色：ADMIN/MEMBER',
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_username (username),
    INDEX idx_user_tenant (tenant_id),
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- -----------------------------------------------------------
-- 3. API Key 表（下游调用凭证）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_key (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT         NOT NULL COMMENT '所属租户 ID',
    user_id         BIGINT                  COMMENT '关联员工 ID（NULL 表示未绑定）',
    key_value       VARCHAR(100)   NOT NULL COMMENT 'Key 值（sk- 开头）',
    name            VARCHAR(100)   NOT NULL COMMENT 'Key 名称',
    monthly_limit   DECIMAL(16,4)           COMMENT '月用量上限（美元），NULL 表示不限',
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED/EXPIRED',
    expires_at      DATETIME                COMMENT '过期时间，NULL 表示永不过期',
    rate_limit_per_minute INT              COMMENT '每分钟最大请求数，NULL 表示不限速',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_key_value (key_value),
    INDEX idx_api_key_tenant (tenant_id),
    CONSTRAINT fk_api_key_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Key 表';

-- -----------------------------------------------------------
-- 4. 上游供应商配置表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(50)    NOT NULL COMMENT '供应商名称',
    api_url         VARCHAR(300)   NOT NULL COMMENT 'API 基础 URL',
    api_key         VARCHAR(300)   NOT NULL COMMENT 'API 密钥',
    priority        INT            NOT NULL DEFAULT 1 COMMENT '优先级（1=主路由，2=备用）',
    timeout         INT            NOT NULL DEFAULT 10000 COMMENT '超时时间（毫秒）',
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    model_mapping   VARCHAR(1000)           COMMENT '模型映射（JSON）',
    input_price_per_million  DECIMAL(16,8) NOT NULL DEFAULT 1.00000000 COMMENT '每百万输入 Token 的美元价格',
    output_price_per_million DECIMAL(16,8) NOT NULL DEFAULT 2.00000000 COMMENT '每百万输出 Token 的美元价格',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上游供应商配置表';

-- -----------------------------------------------------------
-- 5. API 调用日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_call_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT         NOT NULL COMMENT '所属租户 ID',
    api_key_id        BIGINT         NOT NULL COMMENT '使用的 API Key ID',
    model             VARCHAR(100)            COMMENT '请求模型名称',
    prompt_tokens     INT            DEFAULT 0 COMMENT '输入 Token 数',
    completion_tokens INT            DEFAULT 0 COMMENT '输出 Token 数',
    total_tokens      INT            DEFAULT 0 COMMENT '总 Token 数',
    cost              DECIMAL(16,4)  NOT NULL DEFAULT 0.0000 COMMENT '本次调用费用（美元）',
    provider          VARCHAR(50)             COMMENT '实际使用的供应商',
    duration          BIGINT                  COMMENT '请求耗时（毫秒）',
    status            VARCHAR(20)    NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/FALLBACK',
    request_body      VARCHAR(500)            COMMENT '请求体摘要（前 500 字符）',
    error_message     VARCHAR(500)            COMMENT '错误信息',
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_call_log_tenant (tenant_id),
    INDEX idx_call_log_api_key (api_key_id),
    INDEX idx_call_log_created (created_at),
    CONSTRAINT fk_call_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_call_log_api_key FOREIGN KEY (api_key_id) REFERENCES api_key(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API 调用日志表';

-- -----------------------------------------------------------
-- 6. 初始数据：默认供应商配置
-- -----------------------------------------------------------
INSERT INTO provider_config (name, api_url, api_key, priority, timeout, status, model_mapping, input_price_per_million, output_price_per_million)
VALUES
    ('DeepSeek', 'https://api.deepseek.com', 'your-deepseek-api-key', 1, 10000, 'ACTIVE',
     '{"deepseek-chat":"deepseek-chat","deepseek-coder":"deepseek-coder"}', 0.27000000, 1.10000000),
    ('Groq', 'https://api.groq.com', 'your-groq-api-key', 2, 15000, 'ACTIVE',
     '{"deepseek-chat":"llama-3.1-70b-versatile","deepseek-coder":"llama-3.1-70b-versatile"}', 0.05000000, 0.08000000)
ON DUPLICATE KEY UPDATE name = name;

-- -----------------------------------------------------------
-- 7. 超级管理员由应用启动时自动创建（SuperAdminInitializer）
-- 默认账号: admin / admin123
-- -----------------------------------------------------------
