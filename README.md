<div align="center">

**智能 AI 面试官平台** - 基于大语言模型的简历分析和模拟面试系统

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-AGPL--3.0-green)](LICENSE)

</div>

## 项目介绍

AI-Interview 是一个集成了简历分析、模拟面试和知识库管理的智能面试辅助平台。系统利用大语言模型（LLM）和向量数据库技术，为求职者和 HR 提供智能化的简历评估和面试练习服务。

## 核心功能

- 简历智能解析与多维度分析报告
- 基于简历的个性化模拟面试
- 智能追问，还原真实面试场景
- PDF 简历分析报告导出
- 知识库 RAG 问答系统
- PDF 模拟面试评估报告导出

## 系统架构

```
┌─────────────────────────────────────────────────────┐
│                    Frontend (React)                  │
│               http://localhost:5173                 │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP/REST + SSE
┌──────────────────────▼──────────────────────────────┐
│                   Backend (Spring Boot)              │
│                  http://localhost:8080               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │   Resume    │  │  Interview  │  │ Knowledge   │  │
│  │   Module    │  │   Module    │  │    Base     │  │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  │
│         │                │                │           │
│  ┌──────▼────────────────▼────────────────▼──────┐  │
│  │              AI Service (Spring AI)            │  │
│  │              阿里云 DashScope Qwen              │  │
│  └─────────────────────────────────────────────────┘  │
└──────┬──────────────┬───────────────┬────────────────┘
       │              │               │
┌──────▼─────┐ ┌──────▼─────┐ ┌──────▼─────┐
│ PostgreSQL │ │    Redis    │ │  S3 Storage │
│  + pgvector│ │   Stream    │ │   (MinIO)   │
└────────────┘ └────────────┘ └────────────┘
```

## 技术栈

### 后端技术

| 技术                  | 版本  | 说明                      |
| --------------------- | ----- | ------------------------- |
| Spring Boot           | 4.0   | 应用框架                  |
| Java                  | 21    | 开发语言                  |
| Spring AI             | 2.0   | AI 集成框架               |
| PostgreSQL + pgvector | 14+   | 关系数据库 + 向量存储     |
| Redis                 | 6+    | 缓存 + 消息队列（Stream） |
| Apache Tika           | 2.9.2 | 文档解析                  |
| iText 8               | 8.0.5 | PDF 导出                  |
| MapStruct             | 1.6.3 | 对象映射                  |
| Gradle                | 8.14  | 构建工具                  |

### 前端技术

| 技术          | 版本  | 说明     |
| ------------- | ----- | -------- |
| React         | 18.3  | UI 框架  |
| TypeScript    | 5.6   | 开发语言 |
| Vite          | 5.4   | 构建工具 |
| Tailwind CSS  | 4.1   | 样式框架 |
| React Router  | 7.11  | 路由管理 |
| Framer Motion | 12.23 | 动画库   |
| Recharts      | 3.6   | 图表库   |
| Lucide React  | 0.468 | 图标库   |

## 项目结构

```
interview-guide/
├── app/                              # 后端应用
│   └── src/main/java/interview/guide/
│       ├── common/                   # 通用模块 (配置、异常、响应)
│       ├── infrastructure/           # 基础设施 (文件、Redis、存储、PDF导出)
│       └── modules/                  # 业务模块 (resume/interview/knowledgebase)
├── frontend/                         # 前端应用
│   └── src/
│       ├── api/                      # API 接口
│       ├── components/               # 公共组件
│       ├── pages/                    # 页面组件
│       ├── types/                    # 类型定义
│       └── utils/                    # 工具函数
├── .env.example                      # 环境变量模板
└── docker-compose.yml                # Docker 编排
```

## 功能详情

### 简历管理模块

- **多格式解析**：支持 PDF、DOCX、DOC、TXT 等多种简历格式
- **异步处理流**：基于 Redis Stream 实现异步简历分析，支持实时查看处理进度（待分析/分析中/已完成/失败）
- **稳定性保障**：内置分析失败自动重试机制（最多 3 次）与基于内容哈希的重复检测
- **分析报告导出**：支持将 AI 分析结果一键导出为结构化的 PDF 简历分析报告

### 模拟面试模块

- **个性化出题**：基于简历内容智能生成针对性的面试题目，支持实时问答交互
- **智能追问流**：支持配置多轮智能追问（默认 1 条），构建模拟真实场景的线性问答流
- **分批评估机制**：采用分批评估策略（默认每批 8 条），规避大模型 Token 溢出风险
- **智能汇总建议**：对分批评估结果进行二次汇总，提供多维度的改进建议
- **报告一键导出**：支持异步生成并导出详细的 PDF 模拟面试评估报告

### 知识库管理模块

- **文档智能处理**：支持 PDF、DOCX、Markdown 等多种格式文档的自动上传、分块与异步向量化
- **RAG 检索增强**：集成向量数据库，通过检索增强生成（RAG）提升 AI 问答的准确性
- **流式响应交互**：基于 SSE 技术实现打字机式流式响应
- **智能问答对话**：支持基于知识库内容的智能问答，并提供直观的知识库统计信息

## 环境要求

| 依赖          | 版本 | 说明                  |
| ------------- | ---- | --------------------- |
| JDK           | 21+  | 必需                  |
| Node.js       | 18+  | 必需                  |
| PostgreSQL    | 14+  | 必需 (需 pgvector 扩展)|
| Redis         | 6+   | 必需                  |
| S3 兼容存储   | -    | 必需                  |

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/sakurawwwxh/AI-interview.git
cd AI-interview
```

### 2. 配置数据库

```sql
CREATE DATABASE interview_guide;
CREATE EXTENSION vector;
```

### 3. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入以下配置：
# AI_BAILIAN_API_KEY=your_api_key
```

### 4. 启动服务

**后端：**
```bash
./gradlew bootRun
```

**前端：**
```bash
cd frontend
pnpm install
pnpm dev
```

访问 `http://localhost:5173`

### 配置说明

编辑 `app/src/main/resources/application.yml`：

| 配置项              | 说明                    | 默认值                      |
| ------------------- | ---------------------- | -------------------------- |
| `POSTGRES_HOST`     | PostgreSQL 地址        | localhost                   |
| `POSTGRES_PORT`     | PostgreSQL 端口        | 5432                       |
| `POSTGRES_DB`        | 数据库名              | interview_guide            |
| `POSTGRES_PASSWORD`  | 数据库密码            | 123456                     |
| `REDIS_HOST`         | Redis 地址            | localhost                   |
| `APP_STORAGE_ENDPOINT`| S3 存储地址           | http://localhost:9000     |
| `CORS_ALLOWED_ORIGINS`| 允许的跨域来源       | localhost:5173,5174,80     |

> 首次启动设置 `ddl-auto: create`，表创建完成后改为 `update`

## Docker 部署

```bash
docker-compose up -d --build
```

启动后服务地址：

| 服务             | 地址                          | 说明         |
| ---------------- | ----------------------------- | ------------ |
| 前端应用         | http://localhost              | 用户访问入口  |
| 后端 API         | http://localhost:8080         | Swagger 文档 |
| MinIO 控制台     | http://localhost:9001         | minioadmin   |
| PostgreSQL       | localhost:5432                | postgres     |
| Redis            | localhost:6379                | -            |

## 使用场景

| 用户角色        | 使用场景                               |
| --------------- | -------------------------------------- |
| **求职者**      | 上传简历获取分析建议，进行模拟面试练习 |
| **HR/招聘人员** | 批量分析简历，评估候选人能力           |
| **培训机构**    | 提供面试培训服务，管理知识库资源       |

## 安全说明

- 敏感配置（数据库密码、API Key、存储密钥）通过环境变量注入，不要提交到代码仓库
- 生产部署务必修改默认密码和 API Key
- `.gitignore` 已配置忽略敏感文件

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

[AGPL-3.0 License](LICENSE) - 只要通过网络提供服务，就必须向用户公开修改后的源码
