# ModelCore - 企业级 AI API 网关与计费系统
# ModelCore - Enterprise AI API Gateway & Billing System

> **中文**：面向企业场景的 AI 网关系统，提供统一模型接入、智能降级、实时计费、多租户隔离与可观测能力。
> **English**: An enterprise-focused AI gateway that provides unified model access, intelligent fallback, real-time billing, multi-tenant isolation, and observability.

---

## 项目概览 | Project Overview

### 中文
ModelCore 是一个 B2B SaaS 场景的 AI 网关平台，核心目标是帮助企业以统一接口接入多家大模型供应商，同时保障稳定性、可控成本和团队协作效率。

### English
ModelCore is an AI gateway platform designed for B2B SaaS scenarios. Its goal is to help teams integrate multiple LLM providers through one unified API while ensuring reliability, cost control, and operational efficiency.

---

## 核心能力 | Key Capabilities

### 中文
- **多租户隔离**：完整 RBAC 权限模型，租户数据严格隔离。
- **智能降级与容灾**：主供应商超时或 5xx 自动切换备用供应商。
- **高并发计费引擎**：Redis + Lua 原子扣减，预扣与确认双阶段模型。
- **OpenAI 兼容接口**：标准 `/v1/chat/completions`，支持 SSE 流式输出。
- **审计与可观测**：调用日志、筛选查询、统计看板，便于运营与对账。

### English
- **Multi-tenant isolation**: Strict tenant data isolation with full RBAC model.
- **Intelligent fallback**: Automatic failover when primary provider times out or returns 5xx.
- **High-concurrency billing engine**: Redis + Lua atomic deduction with two-phase reserve/confirm flow.
- **OpenAI-compatible API**: Standard `/v1/chat/completions` endpoint with SSE streaming support.
- **Audit and observability**: API call logs, filtering, and dashboard metrics for operations and reconciliation.

---

## 技术亮点（适合招聘展示） | Engineering Highlights (Portfolio Ready)

### 中文
- 使用 **Spring Boot 3 + WebFlux** 构建响应式 AI 请求转发链路。
- 通过 **Redis Lua 脚本**保证计费扣减原子性，避免并发超扣。
- 采用 **JWT + API Key 双通道鉴权**，区分控制台与 API 调用安全边界。
- 具备 **供应商抽象层**，可快速扩展新的上游模型服务。
- 具备完整后台管理界面（租户、Key、日志、统计）。

### English
- Built with **Spring Boot 3 + WebFlux** for reactive AI proxying.
- Uses **Redis Lua scripts** for atomic billing operations under concurrency.
- Implements **dual-path auth (JWT + API Key)** for admin console and API boundary separation.
- Includes a **provider abstraction layer** for fast upstream model integration.
- Ships with an admin console for tenant, key, logs, and metrics management.

---

## 技术栈 | Tech Stack

| 组件 / Component | 技术 / Technology |
|---|---|
| 后端 / Backend | Spring Boot 3.2.5 + WebFlux |
| 安全 / Security | Spring Security + JWT |
| ORM | Spring Data JPA + Hibernate |
| 数据库 / DB | MariaDB 10.6+ |
| 缓存 / Cache | Redis 7.x (Lettuce) |
| 前端 / Frontend | Thymeleaf + Bootstrap 5 + Chart.js |
| 构建 / Build | Maven |
| 运行环境 / Runtime | JDK 21 |

---

## 快速启动 | Quick Start

### 1) 创建数据库 | Create Database
```sql
CREATE DATABASE model_core DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

或执行建表脚本 / Or run schema script:
```bash
mysql -u root -p < src/main/resources/schema.sql
```

### 2) 配置应用 | Configure Application
编辑 `src/main/resources/application.yml`：

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

provider:
  primary:
    api-key: your-deepseek-api-key
  fallback:
    api-key: your-groq-api-key
```

### 3) 编译运行 | Build & Run
```bash
mvn clean package -DskipTests
java -jar target/model-core-1.0.0.jar
```

或直接运行 / Or run directly:
```bash
mvn spring-boot:run
```

### 4) 初始化租户 | Initialize Tenant
访问 `http://localhost:8080/register` 注册首个租户。
Open `http://localhost:8080/register` and create the first tenant.

---

## API 示例 | API Example

### 流式请求 SSE | Streaming SSE
```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "你好 / Hello"}],
    "stream": true
  }'
```

### 非流式请求 | Non-streaming
```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "你好 / Hello"}],
    "stream": false
  }'
```

---

## 项目结构 | Project Structure

```text
model-core/
├── pom.xml
├── src/main/java/com/modelcore/
│   ├── controller/      # API 与后台控制器 / API & admin controllers
│   ├── service/         # 业务服务层 / business services
│   ├── security/        # 鉴权与租户上下文 / auth & tenant context
│   ├── provider/        # 上游供应商抽象 / upstream providers
│   ├── repository/      # 数据访问层 / data access
│   └── entity/          # 领域实体 / domain entities
└── src/main/resources/
    ├── application*.yml
    ├── schema.sql
    ├── lua/
    ├── templates/
    └── static/
```

---

## 生产部署环境变量 | Production Environment Variables

| 变量 / Variable | 说明 / Description |
|---|---|
| `DB_HOST` | 数据库地址 / Database host |
| `DB_PORT` | 数据库端口 / Database port |
| `DB_NAME` | 数据库名 / Database name |
| `DB_USERNAME` | 数据库用户 / Database user |
| `DB_PASSWORD` | 数据库密码 / Database password |
| `REDIS_HOST` | Redis 地址 / Redis host |
| `REDIS_PORT` | Redis 端口 / Redis port |
| `REDIS_PASSWORD` | Redis 密码 / Redis password |
| `JWT_SECRET` | JWT 密钥（建议 >= 256 位） / JWT secret (>= 256 bits) |
| `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `GROQ_API_KEY` | Groq API Key |

启动命令 / Startup command:
```bash
java -jar model-core-1.0.0.jar --spring.profiles.active=prod
```

---

## 适用场景 | Use Cases

### 中文
- 企业内部 AI 能力网关统一出口
- 多模型路由与故障切换
- 按租户/团队计费与用量审计
- SaaS 平台 AI 能力变现基础设施

### English
- Unified AI gateway for enterprise teams
- Multi-model routing and failover
- Tenant/team-based billing and usage audit
- Foundation for AI monetization in SaaS products

---

## 联系方式 | Contact

### 中文
- 商务合作请优先通过 Upwork 平台私信联系。
- 备用邮箱（QQ）：`543976400@qq.com`
- 通常会在 24 小时内回复。

### English
- For project inquiries, please contact me via Upwork first.
- Backup email (QQ Mail): `543976400@qq.com`
- I usually respond within 24 hours.

---

## 开源许可 | License

本项目采用 [Apache License 2.0](LICENSE)。
This project is licensed under [Apache License 2.0](LICENSE).
