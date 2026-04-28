# ModelCore - Enterprise AI API Gateway & Billing System

企业级 AI API 网关与计费系统（B2B SaaS），为企业客户提供统一的 AI 模型接入、智能降级、实时计费和团队管理能力。

## 核心特性

- **多租户隔离**：完整的 RBAC 权限体系，租户间数据严格隔离
- **智能降级**：主供应商超时/5xx 自动无感切换备用供应商，确保服务可用性
- **高并发计费**：Redis + Lua 原子扣减，预扣 → 二次确认模式，精度 4 位小数
- **审计日志**：每次 API 调用完整记录，支持按 Key/日期/模型筛选
- **OpenAI 兼容**：对外暴露标准 `/v1/chat/completions` 接口，支持流式 SSE

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5 + WebFlux |
| 安全认证 | Spring Security + JWT |
| ORM | Spring Data JPA + Hibernate |
| 数据库 | MariaDB 10.6+（LGPL，非 GPL） |
| 缓存 | Redis 7.x（Lettuce 响应式驱动） |
| 前端 | Thymeleaf + Bootstrap 5 + Chart.js |
| 构建 | Maven |
| JDK | 21 |

## 环境要求

- JDK 21+
- MariaDB 10.6+
- Redis 7.x
- Maven 3.9+

## 快速启动

### 1. 创建数据库

```sql
CREATE DATABASE model_core DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

或执行完整建表脚本：

```bash
mysql -u root -p < src/main/resources/schema.sql
```

### 2. 修改配置

编辑 `src/main/resources/application.yml`，配置数据库和 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/model_core
    username: root
    password: your-password
  data:
    redis:
      host: localhost
      port: 6379
```

配置上游供应商 API Key：

```yaml
provider:
  primary:
    api-key: your-deepseek-api-key
  fallback:
    api-key: your-groq-api-key
```

### 3. 编译运行

```bash
mvn clean package -DskipTests
java -jar target/model-core-1.0.0.jar
```

或使用 Maven 直接运行：

```bash
mvn spring-boot:run
```

### 4. 访问后台

浏览器打开 `http://localhost:8080/register`，注册第一个租户。

## API 使用示例

### 注册租户并创建 API Key

1. 访问 `http://localhost:8080/register` 注册租户（如 `Acme Corp`）
2. 登录后台，进入 **API Key** 页面，点击 **创建 Key**
3. 系统自动生成 `sk-` 开头的 API Key

### 调用聊天接口

**流式请求（SSE）：**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": true
  }'
```

**非流式请求：**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": false
  }'
```

### 响应格式

兼容 OpenAI Chat Completion 标准格式。

## 项目结构

```
model-core/
├── pom.xml
├── src/main/java/com/modelcore/
│   ├── ModelCoreApplication.java        # 主启动类
│   ├── entity/                          # JPA 实体
│   │   ├── Tenant.java                  # 租户
│   │   ├── User.java                    # 用户
│   │   ├── ApiKey.java                  # API Key
│   │   ├── ProviderConfig.java          # 供应商配置
│   │   └── ApiCallLog.java              # 调用日志
│   ├── repository/                      # 数据访问层
│   ├── service/                         # 业务服务层
│   │   ├── TokenDeductionService.java   # 计费引擎（Redis + Lua）
│   │   ├── LoadBalancedChatService.java  # 负载均衡 + 智能降级
│   │   ├── ApiKeyService.java           # Key 管理
│   │   ├── TenantService.java           # 租户管理
│   │   ├── UserService.java             # 用户管理
│   │   ├── AuditLogService.java         # 审计日志
│   │   └── BalanceSyncTask.java         # 余额同步定时任务
│   ├── provider/                        # 上游供应商
│   │   ├── ProviderClient.java          # 统一接口
│   │   ├── DeepSeekProvider.java        # DeepSeek 实现
│   │   ├── GroqProvider.java            # Groq 实现
│   │   └── ProviderInitializer.java     # 初始化 + 健康检查
│   ├── security/                        # 安全认证
│   │   ├── SecurityConfig.java          # 双链路安全配置
│   │   ├── JwtTokenProvider.java        # JWT 工具
│   │   ├── ApiKeyAuthFilter.java        # API Key 认证过滤器
│   │   ├── TenantAwareFilter.java       # 租户上下文过滤器
│   │   └── TenantContext.java           # ThreadLocal 上下文
│   └── controller/                      # 控制器
│       ├── ChatProxyController.java     # AI 代理（SSE 流式转发）
│       ├── AuthController.java          # 登录注册
│       ├── DashboardController.java     # 仪表盘
│       ├── ApiKeyController.java        # Key 管理
│       ├── LogController.java           # 调用日志
│       └── StatsApiController.java      # 统计 REST API
├── src/main/resources/
│   ├── application.yml                  # 主配置
│   ├── application-dev.yml              # 开发环境
│   ├── application-prod.yml             # 生产环境
│   ├── schema.sql                       # 建表脚本
│   ├── logback-spring.xml               # 日志配置
│   ├── lua/                             # Redis Lua 脚本
│   ├── templates/                       # Thymeleaf 模板
│   └── static/                          # 静态资源
└── LICENSE
```

## 生产部署

### 环境变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `DB_HOST` | 数据库地址 | `db.example.com` |
| `DB_PORT` | 数据库端口 | `3306` |
| `DB_NAME` | 数据库名 | `model_core` |
| `DB_USERNAME` | 数据库用户名 | `modelcore` |
| `DB_PASSWORD` | 数据库密码 | `***` |
| `REDIS_HOST` | Redis 地址 | `redis.example.com` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | `***` |
| `JWT_SECRET` | JWT 签名密钥 | `至少 256 位` |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | `sk-***` |
| `GROQ_API_KEY` | Groq API Key | `gsk_***` |

### 启动命令

```bash
java -jar model-core-1.0.0.jar --spring.profiles.active=prod
```

## 开源许可

本项目源代码采用 [Apache License 2.0](LICENSE) 许可证。

---

## 商业使用 EULA 附加条款（模板）

> **Enterprise Use License Agreement (EULA) — ModelCore**
>
> Copyright (c) 2026 ModelCore Contributors. All Rights Reserved.
>
> **1. 授权范围**
>
> 本 EULA 作为 Apache License 2.0 的补充条款，适用于 ModelCore 的商业分发和企业部署场景。购买商业许可的客户获得以下额外权利：
>
> - 在不公开源代码的前提下，将 ModelCore 作为内部系统或 SaaS 产品的组成部分进行部署和运营
> - 获得优先技术支持和安全补丁
> - 移除产品中的开源署名要求（仅限商业许可版本）
>
> **2. 限制条款**
>
> - 未经授权，不得将 ModelCore 源代码或其衍生作品作为独立产品进行再销售
> - 不得移除或修改代码中的版权声明和许可证头部
> - 商业许可不包含上游 AI 供应商（DeepSeek、Groq 等）的 API 使用权，客户需自行与供应商签署协议
>
> **3. 免责声明**
>
> 本软件按"现状"提供，不附带任何明示或暗示的担保，包括但不限于对适销性、特定用途适用性和非侵权性的担保。在任何情况下，作者或版权持有人均不对因使用本软件而产生的任何索赔、损害或其他责任承担责任。
>
> **4. 第三方组件声明**
>
> ModelCore 使用以下关键第三方组件，均为非 GPL 许可：
>
> | 组件 | 许可证 | 说明 |
> |------|--------|------|
> | MariaDB Connector/J | LGPL 2.1 | 数据库驱动，LGPL 允许商业使用 |
> | Spring Boot | Apache 2.0 | 应用框架 |
> | Lettuce | Apache 2.0 | Redis 客户端 |
> | jjwt | Apache 2.0 | JWT 库 |
> | Bootstrap | MIT | 前端 UI 框架 |
> | Chart.js | MIT | 图表库 |
>
> **注意**：本项目明确**不使用** MySQL Connector/J（GPL 许可），以避免 GPL 传染风险。
>
> **5. 联系方式**
>
> 如需购买商业许可或咨询企业部署方案，请联系：license@modelcore.example.com
