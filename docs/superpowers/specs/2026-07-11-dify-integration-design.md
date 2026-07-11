# Dify 集成设计方案

**日期**: 2026-07-11  
**状态**: 已批准  
**作者**: Claude Code

## 1. 概述

### 1.1 目标

将 Dify 平台集成到 interview-guide 项目中，用于知识库的创建与管理，同时保留现有的 Spring AI + pgvector 实现作为本地备份和低延迟查询路径。

### 1.2 核心需求

- **知识库管理**: 文档上传、分块、向量化、检索
- **对话应用**: 基于知识库的多轮对话
- **工作流编排**: 复杂的 AI 工作流，如多步骤推理、条件分支
- **双向同步**: 本地和 Dify 知识库保持一致

### 1.3 技术选型

- **Dify 版本**: 自部署（阿里云服务器 8.163.23.204）
- **同步方案**: 混合方案（本地实时推送 + 定时拉取 Dify 变更）
- **本地保留**: Spring AI + pgvector 作为备份和低延迟路径

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Spring Boot 应用                        │
├─────────────────────────────────────────────────────────────┤
│  KnowledgeBaseController                                    │
│       ↓                                                     │
│  KnowledgeBaseService                                       │
│       ├──→ pgvector（本地向量化存储）                          │
│       └──→ DifySyncService（实时推送到 Dify）                 │
│                                                             │
│  DifySyncScheduler（定时拉取 Dify 变更）                      │
└─────────────────────────────────────────────────────────────┘
                           ↕
┌─────────────────────────────────────────────────────────────┐
│                    Dify 实例（阿里云）                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  知识库 API  │  │  对话应用    │  │  工作流     │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 组件职责

| 组件 | 职责 |
|------|------|
| KnowledgeBaseController | 提供 REST API，处理用户请求 |
| KnowledgeBaseService | 知识库核心业务逻辑 |
| KnowledgeBaseVectorService | 本地向量化（pgvector） |
| DifyApiClient | Dify API 封装 |
| DifySyncService | 双向同步逻辑 |
| DifySyncScheduler | 定时拉取任务 |

## 3. 详细设计

### 3.1 Dify 配置类

```java
@Configuration
@ConfigurationProperties(prefix = "dify")
@Data
public class DifyConfig {
    /** Dify API 基础地址 */
    private String apiUrl = "http://8.163.23.204:8080/v1";
    
    /** API Key */
    private String apiKey;
    
    /** 默认知识库 ID */
    private String datasetId;
    
    /** 同步配置 */
    private SyncConfig sync = new SyncConfig();
    
    @Data
    public static class SyncConfig {
        /** 是否启用同步 */
        private boolean enabled = true;
        
        /** 同步间隔（分钟） */
        private int interval = 10;
        
        /** 重试次数 */
        private int retryCount = 3;
        
        /** 重试延迟（毫秒） */
        private long retryDelay = 5000;
    }
}
```

### 3.2 Dify API 客户端

```java
@Service
@Slf4j
public class DifyApiClient {
    
    private final RestClient restClient;
    private final DifyConfig config;
    
    // ========== 知识库操作 ==========
    
    /**
     * 创建文档到 Dify 知识库
     * @param datasetId 知识库 ID
     * @param text 文档内容
     * @param metadata 元数据
     * @return Dify 文档 ID
     */
    public String createDocument(String datasetId, String text, Map<String, Object> metadata);
    
    /**
     * 更新 Dify 文档
     * @param datasetId 知识库 ID
     * @param documentId 文档 ID
     * @param text 新内容
     */
    public void updateDocument(String datasetId, String documentId, String text);
    
    /**
     * 删除 Dify 文档
     * @param datasetId 知识库 ID
     * @param documentId 文档 ID
     */
    public void deleteDocument(String datasetId, String documentId);
    
    /**
     * 列出知识库中的文档
     * @param datasetId 知识库 ID
     * @param page 页码
     * @param limit 每页数量
     * @return 文档列表
     */
    public DifyDocumentList listDocuments(String datasetId, int page, int limit);
    
    // ========== 对话操作 ==========
    
    /**
     * 发送对话消息
     * @param query 用户问题
     * @param conversationId 会话 ID（可选，新建时不传）
     * @param inputs 输入变量
     * @return 对话响应
     */
    public DifyChatResponse chat(String query, String conversationId, Map<String, String> inputs);
    
    /**
     * 获取会话历史
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    public List<DifyMessage> getConversation(String conversationId);
    
    // ========== 工作流操作 ==========
    
    /**
     * 运行工作流
     * @param workflowId 工作流 ID
     * @param inputs 输入参数
     * @return 运行结果
     */
    public DifyWorkflowResponse runWorkflow(String workflowId, Map<String, Object> inputs);
}
```

### 3.3 Dify 同步服务

```java
@Service
@Slf4j
public class DifySyncService {
    
    private final DifyApiClient difyApiClient;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DifySyncLogRepository syncLogRepository;
    private final DifyConfig config;
    
    /**
     * 同步知识库到 Dify（本地 → Dify）
     * 在知识库上传/更新时调用
     * 
     * @param entity 知识库实体
     */
    @Async
    public void syncToDify(KnowledgeBaseEntity entity) {
        // 1. 检查同步是否启用
        // 2. 调用 Dify API 创建/更新文档
        // 3. 更新本地 difyDocumentId
        // 4. 记录同步日志
    }
    
    /**
     * 从 Dify 同步变更（Dify → 本地）
     * 由定时任务调用
     */
    public void syncFromDify() {
        // 1. 获取 Dify 文档列表
        // 2. 对比本地数据
        // 3. 处理新增/更新/删除
        // 4. 记录同步日志
    }
    
    /**
     * 从 Dify 删除文档
     * 在本地知识库删除时调用
     * 
     * @param entity 知识库实体
     */
    public void deleteFromDify(KnowledgeBaseEntity entity);
    
    /**
     * 处理同步冲突
     * 以本地数据为准
     */
    private void resolveConflict(KnowledgeBaseEntity local, DifyDocument remote);
}
```

### 3.4 同步调度器

```java
@Component
@Slf4j
public class DifySyncScheduler {
    
    private final DifySyncService difySyncService;
    private final DifyConfig config;
    
    /**
     * 定时从 Dify 拉取变更
     * 默认每 10 分钟执行一次
     */
    @Scheduled(fixedDelayString = "${dify.sync.interval:10} * 60 * 1000")
    public void syncFromDify() {
        if (!config.getSync().isEnabled()) {
            return;
        }
        
        log.info("开始从 Dify 同步变更");
        try {
            difySyncService.syncFromDify();
            log.info("Dify 同步完成");
        } catch (Exception e) {
            log.error("Dify 同步失败: {}", e.getMessage(), e);
        }
    }
}
```

### 3.5 对话服务

```java
@Service
@Slf4j
public class DifyChatService {
    
    private final DifyApiClient difyApiClient;
    
    /**
     * 基于 Dify 进行对话
     * 
     * @param query 用户问题
     * @param conversationId 会话 ID（可选）
     * @param knowledgeBaseIds 知识库 ID 列表
     * @return 对话响应
     */
    public DifyChatResponse chat(String query, String conversationId, List<Long> knowledgeBaseIds) {
        // 1. 构建输入参数（包含知识库 ID）
        // 2. 调用 Dify 对话 API
        // 3. 返回响应
    }
    
    /**
     * 流式对话
     * 
     * @param query 用户问题
     * @param conversationId 会话 ID
     * @return SSE 流
     */
    public Flux<String> chatStream(String query, String conversationId);
}
```

## 4. 数据库设计

### 4.1 扩展 knowledge_bases 表

```sql
-- 添加 Dify 同步相关字段
ALTER TABLE knowledge_bases 
ADD COLUMN dify_document_id VARCHAR(100),
ADD COLUMN dify_sync_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN dify_sync_time TIMESTAMP,
ADD COLUMN dify_sync_error VARCHAR(500);

-- 添加索引
CREATE INDEX idx_kb_dify_document_id ON knowledge_bases(dify_document_id);
CREATE INDEX idx_kb_dify_sync_status ON knowledge_bases(dify_sync_status);

-- 添加注释
COMMENT ON COLUMN knowledge_bases.dify_document_id IS 'Dify 平台文档 ID';
COMMENT ON COLUMN knowledge_bases.dify_sync_status IS 'Dify 同步状态: PENDING/SYNCED/FAILED';
COMMENT ON COLUMN knowledge_bases.dify_sync_time IS '最后同步时间';
COMMENT ON COLUMN knowledge_bases.dify_sync_error IS '同步错误信息';
```

### 4.2 同步日志表

```sql
CREATE TABLE dify_sync_logs (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    dify_document_id VARCHAR(100),
    sync_direction VARCHAR(20) NOT NULL,  -- TO_DIFY / FROM_DIFY
    sync_action VARCHAR(20) NOT NULL,     -- CREATE / UPDATE / DELETE
    sync_status VARCHAR(20) NOT NULL,     -- SUCCESS / FAILED
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT fk_sync_log_kb FOREIGN KEY (knowledge_base_id) 
        REFERENCES knowledge_bases(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_sync_log_kb_id ON dify_sync_logs(knowledge_base_id);
CREATE INDEX idx_sync_log_created ON dify_sync_logs(created_at);

-- 注释
COMMENT ON TABLE dify_sync_logs IS 'Dify 同步日志';
```

### 4.3 实体类

```java
@Entity
@Table(name = "dify_sync_logs")
@Data
public class DifySyncLogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;
    
    @Column(name = "dify_document_id", length = 100)
    private String difyDocumentId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_direction", nullable = false, length = 20)
    private SyncDirection syncDirection;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_action", nullable = false, length = 20)
    private SyncAction syncAction;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private SyncStatus syncStatus;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum SyncDirection {
        TO_DIFY,    // 本地 → Dify
        FROM_DIFY   // Dify → 本地
    }
    
    public enum SyncAction {
        CREATE,
        UPDATE,
        DELETE
    }
    
    public enum SyncStatus {
        SUCCESS,
        FAILED
    }
}
```

## 5. API 设计

### 5.1 知识库管理 API（增强）

| 方法 | 路径 | 说明 | 变更 |
|------|------|------|------|
| POST | /api/knowledgebase/upload | 上传知识库 | 自动同步到 Dify |
| DELETE | /api/knowledgebase/{id} | 删除知识库 | 同步删除 Dify 文档 |
| PUT | /api/knowledgebase/{id} | 更新知识库 | 同步更新 Dify 文档 |

### 5.2 Dify 专属 API

```java
@RestController
@RequestMapping("/api/dify")
@RequiredArgsConstructor
public class DifyController {
    
    private final DifySyncService syncService;
    private final DifyChatService chatService;
    
    /**
     * 手动触发同步
     */
    @PostMapping("/sync")
    public Result<Void> manualSync() {
        syncService.syncFromDify();
        return Result.success(null);
    }
    
    /**
     * 获取同步状态
     */
    @GetMapping("/sync/status")
    public Result<List<DifySyncStatusDTO>> getSyncStatus() {
        // 返回各知识库的同步状态
    }
    
    /**
     * 获取同步日志
     */
    @GetMapping("/sync/logs")
    public Result<List<DifySyncLogEntity>> getSyncLogs(
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(defaultValue = "50") int limit) {
        // 返回同步日志
    }
    
    /**
     * 基于 Dify 对话
     */
    @PostMapping("/chat")
    public Result<DifyChatResponse> chat(@RequestBody DifyChatRequest request) {
        return Result.success(chatService.chat(
            request.query(),
            request.conversationId(),
            request.knowledgeBaseIds()
        ));
    }
    
    /**
     * 流式对话
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody DifyChatRequest request) {
        return chatService.chatStream(request.query(), request.conversationId());
    }
    
    /**
     * 运行工作流
     */
    @PostMapping("/workflow/{workflowId}")
    public Result<DifyWorkflowResponse> runWorkflow(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> inputs) {
        // 调用 Dify 工作流 API
    }
}
```

### 5.3 DTO 定义

```java
// 对话请求
public record DifyChatRequest(
    String query,
    String conversationId,
    List<Long> knowledgeBaseIds,
    Map<String, String> inputs
) {}

// 对话响应
@Data
public class DifyChatResponse {
    private String answer;
    private String conversationId;
    private List<DifyRetrievalSource> retrievalSources;
    
    @Data
    public static class DifyRetrievalSource {
        private String datasetName;
        private String documentName;
        private String content;
        private Double score;
    }
}

// 同步状态
public record DifySyncStatusDTO(
    Long knowledgeBaseId,
    String name,
    String difyDocumentId,
    String syncStatus,
    LocalDateTime lastSyncTime,
    String errorMessage
) {}
```

## 6. 配置文件

### 6.1 application.yml

```yaml
# Dify 配置
dify:
  api-url: http://8.163.23.204:8080/v1
  api-key: ${DIFY_API_KEY:app-xxxxxxxxxx}
  dataset-id: ${DIFY_DATASET_ID:xxxxxxxx}
  sync:
    enabled: true
    interval: 10  # 分钟
    retry-count: 3
    retry-delay: 5000  # 毫秒

# 异步任务配置
spring:
  task:
    execution:
      pool:
        core-size: 2
        max-size: 5
        queue-capacity: 100
    scheduling:
      pool:
        size: 2
```

### 6.2 环境变量

```bash
# Dify 配置
DIFY_API_KEY=app-xxxxxxxxxx
DIFY_DATASET_ID=xxxxxxxx
```

## 7. 错误处理

### 7.1 重试机制

```java
@Service
public class DifyApiClient {
    
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public String createDocument(String datasetId, String text, Map<String, Object> metadata) {
        // API 调用
    }
    
    @Recover
    public String recoverCreateDocument(DifyApiException e, String datasetId, String text, 
                                        Map<String, Object> metadata) {
        log.error("Dify API 调用失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifySyncException("创建文档失败: " + e.getMessage(), e);
    }
}
```

### 7.2 降级策略

```java
@Service
public class DifySyncService {
    
    public void syncToDify(KnowledgeBaseEntity entity) {
        try {
            difyApiClient.createDocument(...);
            entity.setDifySyncStatus(DifySyncStatus.SYNCED);
        } catch (Exception e) {
            log.warn("同步到 Dify 失败，降级到本地模式: {}", e.getMessage());
            entity.setDifySyncStatus(DifySyncStatus.FAILED);
            entity.setDifySyncError(e.getMessage());
            // 不抛出异常，允许继续执行
        }
    }
}
```

### 7.3 冲突处理

**策略**: 以本地数据为准

**原因**: 
1. 用户主要操作入口是本地系统
2. Dify 侧通常是配置/调整
3. 本地数据更可靠

**实现**:
```java
private void resolveConflict(KnowledgeBaseEntity local, DifyDocument remote) {
    // 如果本地有更新，推送到 Dify
    if (local.getLastModifiedAt().isAfter(remote.getUpdatedAt())) {
        syncToDify(local);
    }
    // 如果 Dify 有更新，但本地是主要入口，记录日志
    else {
        log.info("Dify 文档有更新，但以本地为准: kbId={}, difyDocId={}", 
                 local.getId(), remote.getId());
    }
}
```

## 8. 测试策略

### 8.1 单元测试

- DifyApiClient: Mock RestClient，测试请求构建和响应解析
- DifySyncService: Mock DifyApiClient，测试同步逻辑
- DifySyncScheduler: Mock DifySyncService，测试调度逻辑

### 8.2 集成测试

- 测试完整的同步流程
- 测试错误处理和重试机制
- 测试并发场景

### 8.3 端到端测试

- 上传文件 → 验证 Dify 同步
- 删除文件 → 验证 Dify 删除
- 定时任务 → 验证变更拉取

## 9. 部署方案

### 9.1 Dify 部署（阿里云 8.163.23.204）

```yaml
# docker-compose.yml
version: '3'
services:
  dify:
    image: langgenius/dify:latest
    ports:
      - "8080:80"
    environment:
      - SECRET_KEY=your-secret-key
      - CONSOLE_WEB_URL=http://8.163.23.204:8080
    volumes:
      - dify-data:/app/data
    depends_on:
      - postgres
      - redis

  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=dify
      - POSTGRES_USER=dify
      - POSTGRES_PASSWORD=dify-password
    volumes:
      - postgres-data:/var/lib/postgresql/data

  redis:
    image: redis:7
    volumes:
      - redis-data:/data

volumes:
  dify-data:
  postgres-data:
  redis-data:
```

### 9.2 应用配置

```bash
# 启动应用时设置环境变量
export DIFY_API_KEY=app-xxxxxxxxxx
export DIFY_DATASET_ID=xxxxxxxx

# 启动 Spring Boot 应用
java -jar app.jar
```

## 10. 监控与日志

### 10.1 监控指标

- 同步成功率
- 同步延迟
- API 调用次数
- 错误率

### 10.2 日志规范

```java
log.info("开始同步到 Dify: kbId={}, name={}", entity.getId(), entity.getName());
log.info("同步完成: kbId={}, difyDocId={}, status={}", entity.getId(), difyDocId, status);
log.warn("同步失败，降级到本地模式: kbId={}, error={}", entity.getId(), e.getMessage());
log.error("Dify API 调用失败: endpoint={}, error={}", endpoint, e.getMessage(), e);
```

## 11. 安全考虑

### 11.1 API Key 安全

- 使用环境变量存储 API Key
- 不在代码中硬编码
- 定期轮换 API Key

### 11.2 网络安全

- Dify API 使用内网访问（如果部署在同一服务器）
- 配置防火墙规则
- 使用 HTTPS（生产环境）

### 11.3 数据安全

- 敏感数据不传输到 Dify
- 传输加密
- 访问控制

## 12. 实施计划

### 阶段 1：基础设施（1-2天）

1. 部署 Dify 到阿里云
2. 配置数据库和网络
3. 获取 API Key

### 阶段 2：核心功能（3-5天）

1. 实现 DifyApiClient
2. 实现 DifySyncService
3. 数据库迁移
4. 单元测试

### 阶段 3：集成测试（2-3天）

1. 集成测试
2. 错误处理测试
3. 性能测试

### 阶段 4：高级功能（3-5天）

1. 对话服务
2. 工作流集成
3. 监控和日志

### 阶段 5：部署上线（1-2天）

1. 生产环境配置
2. 部署和验证
3. 文档更新

**总预计时间**: 10-17天

## 13. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Dify 服务不可用 | 高 | 降级到本地模式 |
| 同步冲突 | 中 | 以本地为准，记录日志 |
| API 限流 | 中 | 重试机制，指数退避 |
| 数据不一致 | 中 | 定时全量同步 |

## 14. 附录

### 14.1 参考文档

- [Dify API 文档](https://docs.dify.ai/guides/application-publishing/developing-with-apis)
- [Dify 知识库 API](https://docs.dify.ai/guides/knowledge-base/api-based-knowledge-base)
- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)

### 14.2 术语表

| 术语 | 说明 |
|------|------|
| Dify | 开源 LLM 应用开发平台 |
| Dataset | Dify 知识库 |
| Document | Dify 文档 |
| pgvector | PostgreSQL 向量扩展 |
| RAG | 检索增强生成 |
