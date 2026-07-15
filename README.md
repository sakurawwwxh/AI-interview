<div align="center">

**智能 AI 面试官平台**

基于大语言模型的简历分析、模拟面试、知识库 RAG 与专项复练系统

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-AGPL--3.0-green)](LICENSE)

</div>

## 项目介绍

AI-Interview（本仓库 `interview-guide`）是一个面向求职训练的智能面试辅助平台。系统以 Spring Boot + Spring AI 为后端，React + Vite 为前端，结合 PostgreSQL（pgvector）、Redis Stream 与 S3 兼容存储，覆盖从简历解析、岗位对标、模拟面试到错题复练的完整闭环。

## 核心能力

| 模块 | 能力 |
| --- | --- |
| **简历** | 多格式解析、异步 AI 分析、版本管理、PDF 报告导出 |
| **岗位目标** | 维护 JD、简历匹配、优化建议、一键按岗位开模拟面试 |
| **模拟面试** | 模板出题、JD 加权、智能追问、答案暂存、分批评估、进度展示、失败重试 |
| **能力统计** | 多场面试聚合、能力雷达/趋势、薄弱项引导复练 |
| **错题复练** | 低分题自动入库、专项练习与提升记录 |
| **知识库 / RAG** | 文档向量化、SSE 流式问答；可选同步 Dify |
| **账户体系** | 注册登录、JWT、管理员用户管理、个人 BYOK 模型配置 |
| **成长中心** | Dashboard 今日训练、成长行动计划 |

## 系统架构

```
┌─────────────────────────────────────────────────────┐
│              Frontend (React + Vite)                 │
│                 http://localhost:5173                │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP/REST + SSE
┌──────────────────────▼──────────────────────────────┐
│            Backend (Spring Boot 4 / Java 21)         │
│                 http://localhost:8080                │
│  resume · interview · practice · knowledgebase       │
│  target · growth · user · userai · dify              │
│  ┌───────────────────────────────────────────────┐  │
│  │     Spring AI（DashScope 兼容 OpenAI 协议）    │  │
│  └───────────────────────────────────────────────┘  │
└──────┬──────────────┬───────────────┬────────────────┘
       │              │               │
┌──────▼─────┐ ┌──────▼─────┐ ┌──────▼─────┐
│ PostgreSQL │ │    Redis    │ │ S3 / MinIO  │
│  + pgvector│ │  Stream     │ │  对象存储   │
└────────────┘ └────────────┘ └────────────┘
```

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Java | 21 | 语言（虚拟线程） |
| Spring Boot | 4.0 | Web / Security / Validation / Actuator |
| Spring AI | 2.x | Chat / Embedding / pgvector |
| PostgreSQL + pgvector | 14+ | 业务库 + 向量检索 |
| Redis + Redisson | 6+ | 缓存、会话、Stream 异步任务 |
| Apache Tika | 2.9 | 文档解析 |
| iText | 8.x | PDF 导出 |
| MapStruct | 1.6 | 对象映射 |
| JWT (jjwt) | 0.12 | 鉴权 |
| Maven | 3.9+ | 构建 |

### 前端

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| React | 18.3 | UI |
| TypeScript | 5.6 | 类型 |
| Vite | 5.4 | 构建 |
| Tailwind CSS | 4.1 | 样式 |
| React Router | 7.x | 路由 |
| Zustand | 5.x | 登录态 |
| Recharts / Framer Motion | - | 图表与动效 |
| pnpm | 10.x | 包管理 |

## 项目结构

```text
interview-guide/
├── app/                          # 后端（Spring Boot）
│   └── src/main/java/interview/guide/
│       ├── common/               # 配置、异常、限流、异步 Stream 模板
│       ├── infrastructure/       # 文件、Redis、导出、健康检查、可观测性
│       └── modules/
│           ├── resume/           # 简历上传分析
│           ├── interview/        # 模拟面试与评估
│           ├── practice/         # 错题复练
│           ├── knowledgebase/    # 知识库与 RAG
│           ├── target/           # 岗位目标与匹配
│           ├── growth/           # 成长计划
│           ├── user/             # 认证与管理员
│           ├── userai/           # 个人 AI Key（BYOK）
│           └── dify/             # Dify 同步与对话（可选）
├── frontend/                     # 前端（React + Vite）
│   └── src/
│       ├── api/                  # 接口封装
│       ├── components/           # 公共组件
│       ├── pages/                # 页面（Dashboard / 面试 / 复练 / 知识库…）
│       ├── stores/               # 状态
│       └── utils/                # 草稿、模板推荐等工具
├── docker/                       # 数据库初始化等
├── docker-compose.yml            # 一键编排
├── OPERATIONS.md                 # 生产运维说明
├── .env.example                  # 环境变量示例
└── pom.xml                       # Maven 父工程
```

## 功能说明

### 简历

- 支持 PDF / DOCX / DOC / TXT 等格式解析与内容清洗  
- Redis Stream 异步分析，列表可见 PENDING / PROCESSING / COMPLETED / FAILED  
- 失败可重试；内容哈希去重  
- 支持简历文本版本保存与恢复  
- AI 分析报告可导出 PDF  

### 岗位与匹配

- 维护多份目标岗位 JD，可设「当前岗位」  
- 对指定简历做 JD 匹配（得分、匹配技能、缺口、优化建议）  
- 可应用优化稿为新版本；支持「用该岗位开始模拟面试」  

### 模拟面试

- 面试模板（后端综合 / 前端 / 全栈 / 项目深挖等）+ 题目数量配置  
- 有 JD 时按关键词加权题型；AI 失败可降级默认题库并提示  
- 动态追问；答题本地草稿 + 服务端暂存，刷新可恢复  
- 交卷后异步评估：进度百分比、失败一键重试  
- 单批评估可跳过二次 summary 以缩短等待  
- 面试记录独立列表接口，避免简历详情 N+1  
- 评估报告可导出 PDF  

### 复练与成长

- 低分题自动进入复练中心  
- 能力统计：场次趋势、类别均分、薄弱项跳转复练  
- Dashboard 汇总今日训练与成长行动  

### 知识库

- 文档上传、分块、异步向量化（pgvector）  
- RAG 问答 SSE 流式输出  
- 可选对接 Dify（同步文档 / 对话）  

### 账户与运维

- JWT Access + Refresh，Refresh 可吊销  
- 管理员用户管理；个人可配置 BYOK 模型  
- Actuator 健康检查 / Prometheus（详见 [OPERATIONS.md](OPERATIONS.md)）  
- GitHub Actions：后端测试 + 前端构建  

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21+ | 后端 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 18+ | 前端（建议 20/22） |
| pnpm | 10+ | 前端包管理 |
| PostgreSQL | 14+（建议 16） | 需安装 `vector` 扩展 |
| Redis | 6+ | 缓存与 Stream |
| S3 兼容存储 | - | 如 MinIO |

## 快速开始

### 1. 克隆

```bash
git clone https://github.com/sakurawwwxh/AI-interview.git
cd AI-interview
```

### 2. 启动基础设施（推荐）

```bash
docker compose up -d postgres redis minio
```

首次初始化会创建库并启用 pgvector（见 `docker/postgres/init.sql`）。数据库默认名：`interview_guide`。

若不用 Docker，请自行准备 PostgreSQL / Redis / S3，并在库中执行：

```sql
CREATE DATABASE interview_guide;
-- 连接到该库后
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置后端

本地默认读取 `app/src/main/resources/application.yml`（该文件默认被 `.gitignore` 忽略，避免提交密钥）。

请至少配置：

- 数据源：PostgreSQL 地址 / 库名 / 账号密码  
- Redis：Redisson 地址  
- 对象存储：endpoint / access-key / secret-key / bucket  
- AI：`spring.ai.openai.*`（DashScope 兼容模式与 API Key）  
- JWT：`app.jwt.secret`（长度需满足签名要求）  

可选环境变量可参考仓库根目录 `.env.example`（如 Dify 相关）。

生产环境请使用 Profile `production`，见 [OPERATIONS.md](OPERATIONS.md)。

### 4. 启动后端

在仓库根目录：

```bash
mvn -pl app spring-boot:run
```

或：

```bash
mvn -pl app -DskipTests package
java -jar app/target/app-0.0.1-SNAPSHOT.jar
```

默认端口：`http://localhost:8080`

### 5. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

访问：`http://localhost:5173`

### 6. 全量 Docker 部署

```bash
docker compose up -d --build
```

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 前端 | http://localhost | Nginx 入口 |
| 后端 API | http://localhost:8080 | REST / SSE |
| MinIO 控制台 | http://localhost:9001 | 默认见 compose 配置 |
| PostgreSQL | localhost:5432 | 库名 `interview_guide` |
| Redis | localhost:6379 | - |

## 开发与质量

```bash
# 后端测试
mvn -B -ntp -pl app test

# 前端类型检查 + 生产构建
cd frontend && pnpm install && pnpm run build
```

CI 在 push / PR 时自动执行上述检查（`.github/workflows/ci.yml`）。

## 主要页面

| 路径 | 说明 |
| --- | --- |
| `/` | 训练 Dashboard |
| `/upload` | 上传简历 |
| `/history` | 简历库 / 详情 |
| `/interview/:resumeId` | 模拟面试 |
| `/interviews` | 面试记录 |
| `/interview-statistics` | 能力统计 |
| `/practice` | 复练中心 |
| `/job-targets` | 岗位目标 |
| `/knowledgebase` | 知识库管理 / 问答 |
| `/profile` | 个人中心与 AI 配置 |
| `/admin/users` | 用户管理（管理员） |

## 安全提示

- 不要将含真实 API Key、数据库口令的 `application.yml` 提交到公开仓库  
- 生产务必使用强 JWT Secret，并限制 Actuator 暴露范围  
- 对象存储密钥与 CORS 按环境隔离配置  

## 相关文档

- [OPERATIONS.md](OPERATIONS.md) — 生产 Profile、健康检查、指标与质量门禁  
- [docs/dify-deployment-guide.md](docs/dify-deployment-guide.md) — Dify 部署相关（若启用）  

## 贡献

欢迎提交 Issue 与 Pull Request。建议：

1. 从最新 `master` 拉 `feature/<主题>` 分支  
2. 保持提交信息清晰（`feat:` / `fix:` / `docs:` 等）  
3. 确保本地测试与前端 build 通过后再开 PR  

## 许可证

[AGPL-3.0 License](LICENSE) — 若通过网络提供本软件的修改版本服务，需向用户公开对应源码。
