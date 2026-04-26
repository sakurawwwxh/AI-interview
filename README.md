<div align="center">

**智能 AI 面试官平台** - 基于大语言模型的简历分析和模拟面试系统

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)

</div>

## 项目介绍

AI-Interview是一个集成了简历分析、模拟面试和知识库管理的智能面试辅助平台。系统利用大语言模型（LLM）和向量数据库技术，为求职者和 HR 提供智能化的简历评估和面试练习服务。

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

## 功能特性

### 简历管理模块

- **多格式解析**：支持 PDF、DOCX、DOC、TXT 等多种简历格式
- **异步处理流**：基于 Redis Stream 实现异步简历分析，支持实时查看处理进度
- **稳定性保障**：内置分析失败自动重试机制与基于内容哈希的重复检测
- **分析报告导出**：支持将 AI 分析结果一键导出为结构化的 PDF 简历分析报告

### 模拟面试模块

- **个性化出题**：基于简历内容智能生成针对性的面试题目，支持实时问答交互
- **智能追问流**：支持配置多轮智能追问，构建模拟真实场景的线性问答流
- **分批评估机制**：采用分批评估策略，规避大模型 Token 溢出风险
- **智能汇总建议**：对分批评估结果进行二次汇总，提供多维度的改进建议
- **报告一键导出**：支持异步生成并导出详细的 PDF 模拟面试评估报告

### 知识库管理模块

- **文档智能处理**：支持 PDF、DOCX、Markdown 等多种格式文档的自动上传、分块与异步向量化
- **RAG 检索增强**：集成向量数据库，通过检索增强生成（RAG）提升 AI 问答的准确性
- **流式响应交互**：基于 SSE 技术实现打字机式流式响应
- **智能问答对话**：支持基于知识库内容的智能问答

## 快速开始

### 环境要求

| 依赖          | 版本 | 说明                  |
| ------------- | ---- | --------------------- |
| JDK           | 21+  | 必需                  |
| Node.js       | 18+  | 必需                  |
| PostgreSQL    | 14+  | 必需 (需 pgvector 扩展)|
| Redis         | 6+   | 必需                  |
| S3 兼容存储   | -    | 必需                  |

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
export AI_BAILIAN_API_KEY=your_api_key
```

### 4. 修改应用配置

编辑 `app/src/main/resources/application.yml`，配置数据库、Redis、AI API 等信息。

> 首次启动使用 `ddl-auto: create`，表创建成功后改回 `update`

### 5. 启动服务

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

## Docker 部署

```bash
cp .env.example .env
# 编辑 .env 填入 AI 配置
docker-compose up -d --build
```

| 服务             | 地址                          |
| ---------------- | ----------------------------- |
| 前端应用         | http://localhost              |
| 后端 API         | http://localhost:8080         |
| MinIO 控制台     | http://localhost:9001         |
| PostgreSQL       | localhost:5432                |
| Redis            | localhost:6379                 |

## 使用场景

| 用户角色        | 使用场景                               |
| --------------- | -------------------------------------- |
| **求职者**      | 上传简历获取分析建议，进行模拟面试练习 |
| **HR/招聘人员** | 批量分析简历，评估候选人能力           |
| **培训机构**    | 提供面试培训服务，管理知识库资源       |

## 许可证

AGPL-3.0 License
