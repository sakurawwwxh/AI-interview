<div align="center">

**智能 AI 面试官平台** - 基于大语言模型的简历分析和模拟面试系统
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)


</div>


---

## 项目介绍

InterviewGuide 是一个集成了简历分析、模拟面试和知识库管理的智能面试辅助平台。系统利用大语言模型（LLM）和向量数据库技术，为求职者和 HR 提供智能化的简历评估和面试练习服务。

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (React)                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐ │
│  │ 简历上传  │  │ 模拟面试  │  │ 面试记录  │  │ 知识库管理/问答  │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │ HTTP/SSE
┌─────────────────────────────────────────────────────────────────┐
│                     Backend (Spring Boot)                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                     REST Controllers                      │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐     │
│  │ Resume   │  │Interview │  │Knowledge │  │   RAG Chat   │     │
│  │ Service  │  │ Service  │  │  Service │  │   Service    │     │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Spring AI + DashScope (通义千问)              │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
        │                │                    │
┌───────────────┐ ┌─────────────┐ ┌───────────────────┐
│  PostgreSQL   │ │    Redis    │ │   RustFS (S3)     │
│  + pgvector   │ │   Stream    │ │   对象存储         │
└───────────────┘ └─────────────┘ └───────────────────┘
```

---

## 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.3 | 应用框架 |
| Java | 21 | 开发语言 |
| Spring AI | 1.1.2 | AI 集成框架 |
| PostgreSQL + pgvector | 14+ | 关系数据库 + 向量存储 |
| Redis | 6+ | 缓存 + 消息队列（Stream） |
| Apache Tika | - | 文档解析 |
| iText 7 | - | PDF 导出 |
| MapStruct | - | 对象映射 |
| Gradle | - | 构建工具 |

### 前端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| React | 18.3 | UI 框架 |
| TypeScript | 5.6 | 开发语言 |
| Vite | 5.4 | 构建工具 |
| Tailwind CSS | 4.1 | 样式框架 |
| React Router | 7.11 | 路由管理 |
| Framer Motion | 12.23 | 动画库 |
| Recharts | 3.6 | 图表库 |
| Lucide React | - | 图标库 |

## 功能特性

### 简历管理模块

- 多格式支持：PDF、DOCX、DOC、TXT
- 异步分析：上传后立即返回，后台 Redis Stream 处理
- 状态轮询：实时显示分析进度（待分析/分析中/已完成/失败）
- 自动重试：分析失败自动重试（最多 3 次）
- 简历去重：基于内容哈希检测重复
- PDF 报告导出

### 模拟面试模块

- 基于简历生成个性化面试问题
- 实时问答交互
- 多维度评分（技术能力、沟通能力等）
- 面试报告生成和导出
- 雷达图可视化展示
- 面试历史统计

### 知识库管理模块

- 文档上传和自动分块
- 异步向量化处理
- RAG 检索增强生成
- 流式响应（SSE）
- 智能问答对话
- 知识库统计信息

### TODO



## 效果展示

### 简历与面试

简历库：

![](https://oss.javaguide.cn/xingqiu/pratical-project/interview-guide/page-resume-history.png)

简历上传分析：

![](https://oss.javaguide.cn/xingqiu/pratical-project/interview-guide/page-resume-upload-analysis.png)

简历分析详情：

![](https://oss.javaguide.cn/xingqiu/pratical-project/interview-guide/page-resume-analysis-detail.png)

面试记录：

![](https://oss.javaguide.cn/xingqiu/pratical-project/interview-guide/page-interview-history.png)

面试详情：

![](https://oss.javaguide.cn/xingqiu/pratical-project/interview-guide/page-interview-detail.png)

模拟面试：

![](../../../../../Library/Application Support/typora-user-images/page-mock-interview.png)

### 知识库

知识库管理：

![](../../../../../Library/Application Support/typora-user-images/page-knowledge-base-management.png)

问答助手：

![page-qa-assistant](../../../../../Library/Application Support/typora-user-images/page-qa-assistant.png)

## 项目结构

```
interview-guide/
├── app/                              # 后端应用
│   ├── src/main/java/interview/guide/
│   │   ├── App.java                  # 主启动类
│   │   ├── common/                   # 通用模块
│   │   │   ├── config/               # 配置类
│   │   │   ├── exception/            # 异常处理
│   │   │   └── result/               # 统一响应
│   │   ├── infrastructure/           # 基础设施
│   │   │   ├── export/               # PDF 导出
│   │   │   ├── file/                 # 文件处理
│   │   │   ├── redis/                # Redis 服务
│   │   │   └── storage/              # 对象存储
│   │   └── modules/                  # 业务模块
│   │       ├── interview/            # 面试模块
│   │       ├── knowledgebase/        # 知识库模块
│   │       └── resume/               # 简历模块
│   └── src/main/resources/
│       ├── application.yml           # 应用配置
│       └── prompts/                  # AI 提示词模板
│
├── frontend/                         # 前端应用
│   ├── src/
│   │   ├── api/                      # API 接口
│   │   ├── components/               # 公共组件
│   │   ├── pages/                    # 页面组件
│   │   ├── types/                    # 类型定义
│   │   └── utils/                    # 工具函数
│   ├── package.json
│   └── vite.config.ts
│
└── README.md
```

## 快速开始

环境要求：

| 依赖          | 版本 | 必需 |
| ------------- | ---- | ---- |
| JDK           | 21+  | 是   |
| Node.js       | 18+  | 是   |
| PostgreSQL    | 14+  | 是   |
| pgvector 扩展 | -    | 是   |
| Redis         | 6+   | 是   |
| S3 兼容存储   | -    | 是   |

### 1. 克隆项目

```bash
git clone <repository-url>
cd interview-guide
```

### 2. 配置数据库

```sql
-- 创建数据库
CREATE DATABASE interview_guide;

-- 连接数据库并启用 pgvector 扩展（可选，启动后端SpringAI框架底层会自动创建）
CREATE EXTENSION vector;
```

### 3. 配置环境变量

```bash
# AI API 密钥（阿里云 DashScope）
export AI_BAILIAN_API_KEY=your_api_key
```

### 4. 修改应用配置

编辑 `app/src/main/resources/application.yml`：

```yaml
spring:
  # PostgreSQL数据库配置
  datasource:
    url: jdbc:postgresql://localhost:5432/interview_guide
    username: your_username
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379

# RustFS (S3兼容) 存储配置
app:
  storage:
    endpoint: http://localhost:9000
    access-key: your_access_key
    secret-key: your_secret_key
    bucket: interview-guide
    
 # Redisson配置
redisson:
  config: |
    singleServerConfig:
      address: "redis://localhost:6379"
      database: 0
      idleConnectionTimeout: 10000
      connectTimeout: 10000
      timeout: 3000
      retryAttempts: 3
      retryInterval: 1500
      password: null
      subscriptionsPerConnection: 5
      clientName: null
      subscriptionConnectionMinimumIdleSize: 1
      subscriptionConnectionPoolSize: 50
      connectionMinimumIdleSize: 10
      connectionPoolSize: 64
      dnsMonitoringInterval: 5000

```

### 5. 启动服务

**后端：**

```bash
./gradlew bootRun
```

后端服务启动于 `http://localhost:8080`

**前端：**

```bash
cd frontend
pnpm install
pnpm dev
```

前端服务启动于 `http://localhost:5173`

## 异步处理流程

简历分析和知识库向量化采用 **Redis Stream** 异步处理：

```
上传请求 → 保存文件 → 发送消息到 Stream → 立即返回
                              ↓
                      Consumer 消费消息
                              ↓
                    执行分析/向量化任务
                              ↓
                      更新数据库状态
                              ↓
                   前端轮询获取最新状态
```

**状态流转：** `PENDING` → `PROCESSING` → `COMPLETED` / `FAILED`

## 使用场景

| 用户角色 | 使用场景 |
|----------|----------|
| **求职者** | 上传简历获取分析建议，进行模拟面试练习 |
| **HR/招聘人员** | 批量分析简历，评估候选人能力 |
| **培训机构** | 提供面试培训服务，管理知识库资源 |

---

## 常见问题

### Q: 简历分析失败

检查一下阿里云 DashScope API KEY 是否配置正确（申请地址：<https://bailian.console.aliyun.com/>）。

### Q: 简历分析一直显示"分析中"？

检查 Redis 连接和 Stream Consumer 是否正常运行。查看后端日志确认是否有错误。

### Q: 知识库问答没有响应？

确认知识库已完成向量化（状态为 COMPLETED），检查 pgvector 扩展是否正确安装。

### Q: PDF 导出失败？

检查 iText 依赖是否正确，确认字体文件存在。

## 分支说明

| 分支 | 说明 |
|------|------|
| `master` | 主分支，包含完整功能（Redis Stream 异步处理） |
| `v1.0-without-redis` | 基础版本，同步处理，无 Redis 依赖 |

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

AGPL-3.0 License（只要通过网络提供服务，就必须向用户公开修改后的源码）

