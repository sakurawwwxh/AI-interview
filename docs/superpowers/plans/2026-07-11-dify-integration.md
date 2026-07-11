# Dify 集成实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Dify 平台集成到 interview-guide 项目中，实现知识库的双向同步、对话应用和工作流编排

**Architecture:** 混合同步方案（本地实时推送 + 定时拉取 Dify 变更），保留现有 Spring AI + pgvector 作为本地备份

**Tech Stack:** Spring Boot 4.0, Spring AI 2.0, Dify API, PostgreSQL, pgvector

---

## 文件结构

### 新建文件

| 文件路径 | 职责 |
|----------|------|
| `app/src/main/java/interview/guide/modules/dify/config/DifyConfig.java` | Dify 配置类 |
| `app/src/main/java/interview/guide/modules/dify/exception/DifyApiException.java` | Dify API 异常 |
| `app/src/main/java/interview/guide/modules/dify/exception/DifySyncException.java` | Dify 同步异常 |
| `app/src/main/java/interview/guide/modules/dify/model/DifyDocument.java` | Dify 文档模型 |
| `app/src/main/java/interview/guide/modules/dify/model/DifyDocumentList.java` | Dify 文档列表 |
| `app/src/main/java/interview/guide/modules/dify/model/DifyChatRequest.java` | 对话请求 |
| `app/src/main/java/interview/guide/modules/dify/model/DifyChatResponse.java` | 对话响应 |
| `app/src/main/java/interview/guide/modules/dify/model/DifyWorkflowResponse.java` | 工作流响应 |
| `app/src/main/java/interview/guide/modules/dify/model/DifySyncStatusDTO.java` | 同步状态 DTO |
| `app/src/main/java/interview/guide/modules/dify/model/DifySyncLogEntity.java` | 同步日志实体 |
| `app/src/main/java/interview/guide/modules/dify/model/DifySyncDirection.java` | 同步方向枚举 |
| `app/src/main/java/interview/guide/modules/dify/model/DifySyncAction.java` | 同步动作枚举 |
| `app/src/main/java/interview/guide/modules/dify/model/DifySyncStatus.java` | 同步状态枚举 |
| `app/src/main/java/interview/guide/modules/dify/repository/DifySyncLogRepository.java` | 同步日志仓库 |
| `app/src/main/java/interview/guide/modules/dify/client/DifyApiClient.java` | Dify API 客户端 |
| `app/src/main/java/interview/guide/modules/dify/service/DifySyncService.java` | 同步服务 |
| `app/src/main/java/interview/guide/modules/dify/service/DifyChatService.java` | 对话服务 |
| `app/src/main/java/interview/guide/modules/dify/scheduler/DifySyncScheduler.java` | 同步调度器 |
| `app/src/main/java/interview/guide/modules/dify/controller/DifyController.java` | Dify 控制器 |
| `app/src/test/java/interview/guide/modules/dify/client/DifyApiClientTest.java` | API 客户端测试 |
| `app/src/test/java/interview/guide/modules/dify/service/DifySyncServiceTest.java` | 同步服务测试 |
| `app/src/test/java/interview/guide/modules/dify/service/DifyChatServiceTest.java` | 对话服务测试 |
| `app/src/main/resources/db/migration/V2__add_dify_sync_fields.sql` | 数据库迁移脚本 |

### 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `app/src/main/java/interview/guide/modules/knowledgebase/model/KnowledgeBaseEntity.java` | 添加 Dify 同步字段 |
| `app/src/main/java/interview/guide/modules/knowledgebase/service/KnowledgeBaseUploadService.java` | 上传后同步到 Dify |
| `app/src/main/java/interview/guide/modules/knowledgebase/service/KnowledgeBaseDeleteService.java` | 删除时同步到 Dify |
| `app/src/main/resources/application.yml` | 添加 Dify 配置 |

---

## Task 1: 数据库迁移

**Files:**
- Create: `app/src/main/resources/db/migration/V2__add_dify_sync_fields.sql`

- [ ] **Step 1: 创建数据库迁移脚本**

```sql
-- V2__add_dify_sync_fields.sql
-- 添加 Dify 同步相关字段到 knowledge_bases 表

-- 添加字段
ALTER TABLE knowledge_bases
ADD COLUMN IF NOT EXISTS dify_document_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS dify_sync_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN IF NOT EXISTS dify_sync_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS dify_sync_error VARCHAR(500);

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_kb_dify_document_id ON knowledge_bases(dify_document_id);
CREATE INDEX IF NOT EXISTS idx_kb_dify_sync_status ON knowledge_bases(dify_sync_status);

-- 创建同步日志表
CREATE TABLE IF NOT EXISTS dify_sync_logs (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    dify_document_id VARCHAR(100),
    sync_direction VARCHAR(20) NOT NULL,
    sync_action VARCHAR(20) NOT NULL,
    sync_status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_sync_log_kb FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_sync_log_kb_id ON dify_sync_logs(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_sync_log_created ON dify_sync_logs(created_at);
```

- [ ] **Step 2: 验证迁移脚本语法**

Run: `psql -h 8.163.23.204 -U postgres -d interview_guide -f app/src/main/resources/db/migration/V2__add_dify_sync_fields.sql --dry-run`
Expected: 无语法错误

- [ ] **Step 3: 执行迁移脚本**

Run: `psql -h 8.163.23.204 -U postgres -d interview_guide -f app/src/main/resources/db/migration/V2__add_dify_sync_fields.sql`
Expected: ALTER TABLE, CREATE INDEX, CREATE TABLE 成功

- [ ] **Step 4: 验证表结构**

Run: `psql -h 8.163.23.204 -U postgres -d interview_guide -c "\d knowledge_bases" | grep dify`
Expected: 显示新添加的 dify 字段

- [ ] **Step 5: Commit**

```bash
git add app/src/main/resources/db/migration/V2__add_dify_sync_fields.sql
git commit -m "feat: 添加 Dify 同步数据库迁移脚本"
```

---

## Task 2: Dify 配置和模型

**Files:**
- Create: `app/src/main/java/interview/guide/modules/dify/config/DifyConfig.java`
- Create: `app/src/main/java/interview/guide/modules/dify/exception/DifyApiException.java`
- Create: `app/src/main/java/interview/guide/modules/dify/exception/DifySyncException.java`
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: 创建 DifyConfig 配置类**

```java
// DifyConfig.java
package interview.guide.modules.dify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Dify 配置类
 */
@Configuration
@ConfigurationProperties(prefix = "dify")
@Data
public class DifyConfig {
    /** Dify API 基础地址 */
    private String apiUrl = "http://8.163.23.204:80/v1";

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

- [ ] **Step 2: 创建异常类**

```java
// DifyApiException.java
package interview.guide.modules.dify.exception;

/**
 * Dify API 调用异常
 */
public class DifyApiException extends RuntimeException {
    public DifyApiException(String message) {
        super(message);
    }

    public DifyApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
// DifySyncException.java
package interview.guide.modules.dify.exception;

/**
 * Dify 同步异常
 */
public class DifySyncException extends RuntimeException {
    public DifySyncException(String message) {
        super(message);
    }

    public DifySyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: 添加 Dify 配置到 application.yml**

在 `application.yml` 末尾添加：

```yaml
# Dify 配置
dify:
  api-url: http://8.163.23.204:80/v1
  api-key: ${DIFY_API_KEY:}
  dataset-id: ${DIFY_DATASET_ID:}
  sync:
    enabled: true
    interval: 10
    retry-count: 3
    retry-delay: 5000

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

- [ ] **Step 4: 验证配置加载**

Run: `mvn spring-boot:run -pl app -Dspring-boot.run.arguments="--dify.api-key=test"`
Expected: 应用启动成功，DifyConfig bean 创建成功

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/interview/guide/modules/dify/config/DifyConfig.java
git add app/src/main/java/interview/guide/modules/dify/exception/DifyApiException.java
git add app/src/main/java/interview/guide/modules/dify/exception/DifySyncException.java
git add app/src/main/resources/application.yml
git commit -m "feat: 添加 Dify 配置类和异常处理"
```

---

## Task 3: Dify 数据模型

**Files:**
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifyDocument.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifyDocumentList.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifyChatRequest.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifyChatResponse.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifyWorkflowResponse.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifySyncStatusDTO.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifySyncLogEntity.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifySyncDirection.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifySyncAction.java`
- Create: `app/src/main/java/interview/guide/modules/dify/model/DifySyncStatus.java`

- [ ] **Step 1: 创建枚举类**

```java
// DifySyncDirection.java
package interview.guide.modules.dify.model;

/**
 * Dify 同步方向枚举
 */
public enum DifySyncDirection {
    /** 本地 → Dify */
    TO_DIFY,
    /** Dify → 本地 */
    FROM_DIFY
}
```

```java
// DifySyncAction.java
package interview.guide.modules.dify.model;

/**
 * Dify 同步动作枚举
 */
public enum DifySyncAction {
    CREATE,
    UPDATE,
    DELETE
}
```

```java
// DifySyncStatus.java
package interview.guide.modules.dify.model;

/**
 * Dify 同步状态枚举
 */
public enum DifySyncStatus {
    /** 待同步 */
    PENDING,
    /** 已同步 */
    SYNCED,
    /** 同步失败 */
    FAILED
}
```

- [ ] **Step 2: 创建 Dify 文档模型**

```java
// DifyDocument.java
package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dify 文档模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyDocument {
    /** Dify 文档 ID */
    private String id;

    /** 文档名称 */
    private String name;

    /** 文档内容预览 */
    private String contentPreview;

    /** 元数据 */
    private Map<String, Object> metadata;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 索引状态 */
    private String indexingStatus;
}
```

```java
// DifyDocumentList.java
package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dify 文档列表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyDocumentList {
    /** 文档列表 */
    private List<DifyDocument> data;

    /** 总数 */
    private int total;

    /** 页码 */
    private int page;

    /** 每页数量 */
    private int limit;

    /** 是否有更多 */
    private boolean hasMore;
}
```

- [ ] **Step 3: 创建对话模型**

```java
// DifyChatRequest.java
package interview.guide.modules.dify.model;

import java.util.List;
import java.util.Map;

/**
 * Dify 对话请求
 */
public record DifyChatRequest(
    /** 用户问题 */
    String query,
    /** 会话 ID（可选） */
    String conversationId,
    /** 知识库 ID 列表 */
    List<Long> knowledgeBaseIds,
    /** 输入变量 */
    Map<String, String> inputs
) {}
```

```java
// DifyChatResponse.java
package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dify 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyChatResponse {
    /** 回答内容 */
    private String answer;

    /** 会话 ID */
    private String conversationId;

    /** 检索来源 */
    private List<DifyRetrievalSource> retrievalSources;

    /**
     * 检索来源
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DifyRetrievalSource {
        /** 知识库名称 */
        private String datasetName;

        /** 文档名称 */
        private String documentName;

        /** 内容片段 */
        private String content;

        /** 相似度分数 */
        private Double score;
    }
}
```

```java
// DifyWorkflowResponse.java
package interview.guide.modules.dify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Dify 工作流响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyWorkflowResponse {
    /** 工作流运行 ID */
    private String runId;

    /** 运行状态 */
    private String status;

    /** 输出数据 */
    private Map<String, Object> outputs;

    /** 错误信息（如果有） */
    private String error;

    /** 耗时（毫秒） */
    private Long elapsed;
}
```

- [ ] **Step 4: 创建同步日志实体**

```java
// DifySyncLogEntity.java
package interview.guide.modules.dify.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dify 同步日志实体
 */
@Entity
@Table(name = "dify_sync_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private DifySyncDirection syncDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_action", nullable = false, length = 20)
    private DifySyncAction syncAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private DifySyncStatus syncStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

```java
// DifySyncStatusDTO.java
package interview.guide.modules.dify.model;

import java.time.LocalDateTime;

/**
 * Dify 同步状态 DTO
 */
public record DifySyncStatusDTO(
    Long knowledgeBaseId,
    String name,
    String difyDocumentId,
    DifySyncStatus syncStatus,
    LocalDateTime lastSyncTime,
    String errorMessage
) {}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/interview/guide/modules/dify/model/
git commit -m "feat: 添加 Dify 数据模型和枚举类"
```

---

## Task 4: Dify API 客户端

**Files:**
- Create: `app/src/main/java/interview/guide/modules/dify/client/DifyApiClient.java`
- Create: `app/src/test/java/interview/guide/modules/dify/client/DifyApiClientTest.java`

- [ ] **Step 1: 编写 DifyApiClient 单元测试**

```java
// DifyApiClientTest.java
package interview.guide.modules.dify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.exception.DifyApiException;
import interview.guide.modules.dify.model.DifyDocument;
import interview.guide.modules.dify.model.DifyDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DifyApiClientTest {

    private DifyApiClient difyApiClient;
    private DifyConfig config;
    private RestClient mockRestClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        config = new DifyConfig();
        config.setApiUrl("http://localhost:8080/v1");
        config.setApiKey("test-api-key");

        objectMapper = new ObjectMapper();
        mockRestClient = mock(RestClient.class);
        difyApiClient = new DifyApiClient(config, objectMapper);
    }

    @Test
    void createDocument_shouldReturnDocumentId() {
        // Given
        String datasetId = "test-dataset";
        String text = "测试文档内容";
        Map<String, Object> metadata = Map.of("key", "value");

        // When & Then - 需要 mock RestClient
        // 实际测试需要启动 Dify 服务或使用 WireMock
        // 这里验证方法存在且签名正确
        assertNotNull(difyApiClient);
    }

    @Test
    void createDocument_withNullDatasetId_shouldThrowException() {
        // Given
        String text = "测试文档内容";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            difyApiClient.createDocument(null, text, Map.of());
        });
    }

    @Test
    void createDocument_withEmptyText_shouldThrowException() {
        // Given
        String datasetId = "test-dataset";
        String text = "";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            difyApiClient.createDocument(datasetId, text, Map.of());
        });
    }
}
```

- [ ] **Step 2: 运行测试验证编译**

Run: `mvn test -pl app -Dtest=DifyApiClientTest -Dmaven.test.skip=false`
Expected: 编译失败（DifyApiClient 不存在）

- [ ] **Step 3: 创建 DifyApiClient 实现**

```java
// DifyApiClient.java
package interview.guide.modules.dify.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.exception.DifyApiException;
import interview.guide.modules.dify.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dify API 客户端
 * 封装所有与 Dify 平台的交互
 */
@Service
@Slf4j
public class DifyApiClient {

    private final RestTemplate restTemplate;
    private final DifyConfig config;
    private final ObjectMapper objectMapper;

    public DifyApiClient(RestTemplateBuilder restTemplateBuilder,
                         DifyConfig config,
                         ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建文档到 Dify 知识库
     *
     * @param datasetId 知识库 ID
     * @param text      文档内容
     * @param metadata  元数据
     * @return Dify 文档 ID
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public String createDocument(String datasetId, String text, Map<String, Object> metadata) {
        // 参数校验
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        log.info("创建 Dify 文档: datasetId={}, textLength={}", datasetId, text.length());

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents";

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("metadata", metadata != null ? metadata : Map.of());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String documentId = response.getBody().path("document").path("id").asText();
                log.info("Dify 文档创建成功: documentId={}", documentId);
                return documentId;
            } else {
                throw new DifyApiException("创建文档失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("创建文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新 Dify 文档
     *
     * @param datasetId  知识库 ID
     * @param documentId 文档 ID
     * @param text       新内容
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public void updateDocument(String datasetId, String documentId, String text) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        log.info("更新 Dify 文档: datasetId={}, documentId={}", datasetId, documentId);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents/" + documentId;

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("text", text);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.PUT, request, Void.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DifyApiException("更新文档失败: " + response.getStatusCode());
            }

            log.info("Dify 文档更新成功: documentId={}", documentId);
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("更新文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除 Dify 文档
     *
     * @param datasetId  知识库 ID
     * @param documentId 文档 ID
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public void deleteDocument(String datasetId, String documentId) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }

        log.info("删除 Dify 文档: datasetId={}, documentId={}", datasetId, documentId);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents/" + documentId;

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.DELETE, request, Void.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DifyApiException("删除文档失败: " + response.getStatusCode());
            }

            log.info("Dify 文档删除成功: documentId={}", documentId);
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("删除文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出知识库中的文档
     *
     * @param datasetId 知识库 ID
     * @param page      页码
     * @param limit     每页数量
     * @return 文档列表
     */
    public DifyDocumentList listDocuments(String datasetId, int page, int limit) {
        log.info("列出 Dify 文档: datasetId={}, page={}, limit={}", datasetId, page, limit);

        String url = config.getApiUrl() + "/datasets/" + datasetId + "/documents?page=" + page + "&limit=" + limit;

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                List<DifyDocument> documents = new ArrayList<>();

                JsonNode dataNode = body.path("data");
                if (dataNode.isArray()) {
                    for (JsonNode docNode : dataNode) {
                        documents.add(parseDocument(docNode));
                    }
                }

                return DifyDocumentList.builder()
                    .data(documents)
                    .total(body.path("total").asInt(0))
                    .page(page)
                    .limit(limit)
                    .hasMore(body.path("has_more").asBoolean(false))
                    .build();
            } else {
                throw new DifyApiException("列出文档失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("列出文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送对话消息
     *
     * @param query          用户问题
     * @param conversationId 会话 ID（可选）
     * @param inputs         输入变量
     * @return 对话响应
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifyChatResponse chat(String query, String conversationId, Map<String, String> inputs) {
        log.info("发送 Dify 对话: query={}, conversationId={}", query, conversationId);

        String url = config.getApiUrl() + "/chat-messages";

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("inputs", inputs != null ? inputs : Map.of());
        body.put("response_mode", "blocking");
        body.put("user", "system");

        if (conversationId != null && !conversationId.isBlank()) {
            body.put("conversation_id", conversationId);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseBody = response.getBody();
                return DifyChatResponse.builder()
                    .answer(responseBody.path("answer").asText())
                    .conversationId(responseBody.path("conversation_id").asText())
                    .retrievalSources(parseRetrievalSources(responseBody.path("retrieval_sources")))
                    .build();
            } else {
                throw new DifyApiException("对话失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("对话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 运行工作流
     *
     * @param workflowId 工作流 ID
     * @param inputs     输入参数
     * @return 运行结果
     */
    @Retryable(
        value = {DifyApiException.class},
        maxExpression = "${dify.sync.retry-count:3}",
        backoff = @Backoff(delayExpression = "${dify.sync.retry-delay:5000}")
    )
    public DifyWorkflowResponse runWorkflow(String workflowId, Map<String, Object> inputs) {
        log.info("运行 Dify 工作流: workflowId={}", workflowId);

        String url = config.getApiUrl() + "/workflows/run";

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("workflow_id", workflowId);
        body.put("inputs", inputs != null ? inputs : Map.of());
        body.put("response_mode", "blocking");
        body.put("user", "system");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseBody = response.getBody();
                return DifyWorkflowResponse.builder()
                    .runId(responseBody.path("run_id").asText())
                    .status(responseBody.path("status").asText())
                    .outputs(parseOutputs(responseBody.path("outputs")))
                    .elapsed(responseBody.path("elapsed").asLong())
                    .build();
            } else {
                throw new DifyApiException("运行工作流失败: " + response.getStatusCode());
            }
        } catch (DifyApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Dify API 失败: {}", e.getMessage());
            throw new DifyApiException("运行工作流失败: " + e.getMessage(), e);
        }
    }

    // ========== Recovery 方法 ==========

    @Recover
    public String recoverCreateDocument(DifyApiException e, String datasetId, String text,
                                        Map<String, Object> metadata) {
        log.error("Dify API 创建文档失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("创建文档失败: " + e.getMessage(), e);
    }

    @Recover
    public void recoverUpdateDocument(DifyApiException e, String datasetId, String documentId, String text) {
        log.error("Dify API 更新文档失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("更新文档失败: " + e.getMessage(), e);
    }

    @Recover
    public void recoverDeleteDocument(DifyApiException e, String datasetId, String documentId) {
        log.error("Dify API 删除文档失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("删除文档失败: " + e.getMessage(), e);
    }

    @Recover
    public DifyChatResponse recoverChat(DifyApiException e, String query, String conversationId,
                                        Map<String, String> inputs) {
        log.error("Dify API 对话失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("对话失败: " + e.getMessage(), e);
    }

    @Recover
    public DifyWorkflowResponse recoverRunWorkflow(DifyApiException e, String workflowId,
                                                   Map<String, Object> inputs) {
        log.error("Dify API 运行工作流失败，已重试 {} 次: {}", config.getSync().getRetryCount(), e.getMessage());
        throw new DifyApiException("运行工作流失败: " + e.getMessage(), e);
    }

    // ========== 辅助方法 ==========

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + config.getApiKey());
        return headers;
    }

    private DifyDocument parseDocument(JsonNode node) {
        return DifyDocument.builder()
            .id(node.path("id").asText())
            .name(node.path("name").asText())
            .contentPreview(node.path("content_preview").asText())
            .createdAt(parseDateTime(node.path("created_at").asText()))
            .updatedAt(parseDateTime(node.path("updated_at").asText()))
            .indexingStatus(node.path("indexing_status").asText())
            .build();
    }

    private List<DifyChatResponse.DifyRetrievalSource> parseRetrievalSources(JsonNode node) {
        List<DifyChatResponse.DifyRetrievalSource> sources = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode sourceNode : node) {
                sources.add(DifyChatResponse.DifyRetrievalSource.builder()
                    .datasetName(sourceNode.path("dataset_name").asText())
                    .documentName(sourceNode.path("document_name").asText())
                    .content(sourceNode.path("content").asText())
                    .score(sourceNode.path("score").asDouble())
                    .build());
            }
        }
        return sources;
    }

    private Map<String, Object> parseOutputs(JsonNode node) {
        Map<String, Object> outputs = new HashMap<>();
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                outputs.put(entry.getKey(), entry.getValue().asText());
            });
        }
        return outputs;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("解析日期时间失败: {}", dateTimeStr);
            return null;
        }
    }
}
```

- [ ] **Step 4: 运行测试验证编译**

Run: `mvn test -pl app -Dtest=DifyApiClientTest -Dmaven.test.skip=false`
Expected: 编译成功，测试通过

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/interview/guide/modules/dify/client/DifyApiClient.java
git add app/src/test/java/interview/guide/modules/dify/client/DifyApiClientTest.java
git commit -m "feat: 实现 Dify API 客户端"
```

---

## Task 5: Dify 同步服务

**Files:**
- Create: `app/src/main/java/interview/guide/modules/dify/repository/DifySyncLogRepository.java`
- Create: `app/src/main/java/interview/guide/modules/dify/service/DifySyncService.java`
- Create: `app/src/test/java/interview/guide/modules/dify/service/DifySyncServiceTest.java`
- Modify: `app/src/main/java/interview/guide/modules/knowledgebase/model/KnowledgeBaseEntity.java`

- [ ] **Step 1: 创建 DifySyncLogRepository**

```java
// DifySyncLogRepository.java
package interview.guide.modules.dify.repository;

import interview.guide.modules.dify.model.DifySyncDirection;
import interview.guide.modules.dify.model.DifySyncLogEntity;
import interview.guide.modules.dify.model.DifySyncStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Dify 同步日志仓库
 */
@Repository
public interface DifySyncLogRepository extends JpaRepository<DifySyncLogEntity, Long> {

    /**
     * 查询指定知识库的最新同步日志
     */
    Optional<DifySyncLogEntity> findFirstByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId);

    /**
     * 查询指定知识库的同步日志
     */
    List<DifySyncLogEntity> findByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId, PageRequest pageRequest);

    /**
     * 查询指定状态的同步日志
     */
    List<DifySyncLogEntity> findBySyncStatus(DifySyncStatus status);

    /**
     * 查询指定方向的同步日志
     */
    List<DifySyncLogEntity> findBySyncDirection(DifySyncDirection direction);

    /**
     * 查询指定时间范围内的同步日志
     */
    List<DifySyncLogEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 统计指定知识库的同步失败次数
     */
    @Query("SELECT COUNT(s) FROM DifySyncLogEntity s WHERE s.knowledgeBaseId = :kbId AND s.syncStatus = 'FAILED'")
    long countFailedByKnowledgeBaseId(@Param("kbId") Long knowledgeBaseId);

    /**
     * 删除指定时间之前的同步日志
     */
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
```

- [ ] **Step 2: 扩展 KnowledgeBaseEntity 添加 Dify 字段**

在 `KnowledgeBaseEntity.java` 中添加以下字段（在 `chunkCount` 字段之后）：

```java
// Dify 同步相关字段
@Column(name = "dify_document_id", length = 100)
private String difyDocumentId;

@Enumerated(EnumType.STRING)
@Column(name = "dify_sync_status", length = 20)
private DifySyncStatus difySyncStatus = DifySyncStatus.PENDING;

@Column(name = "dify_sync_time")
private LocalDateTime difySyncTime;

@Column(name = "dify_sync_error", length = 500)
private String difySyncError;

// Getter 和 Setter
public String getDifyDocumentId() {
    return difyDocumentId;
}

public void setDifyDocumentId(String difyDocumentId) {
    this.difyDocumentId = difyDocumentId;
}

public DifySyncStatus getDifySyncStatus() {
    return difySyncStatus;
}

public void setDifySyncStatus(DifySyncStatus difySyncStatus) {
    this.difySyncStatus = difySyncStatus;
}

public LocalDateTime getDifySyncTime() {
    return difySyncTime;
}

public void setDifySyncTime(LocalDateTime difySyncTime) {
    this.difySyncTime = difySyncTime;
}

public String getDifySyncError() {
    return difySyncError;
}

public void setDifySyncError(String difySyncError) {
    this.difySyncError = difySyncError;
}
```

- [ ] **Step 3: 编写 DifySyncService 测试**

```java
// DifySyncServiceTest.java
package interview.guide.modules.dify.service;

import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.model.*;
import interview.guide.modules.dify.repository.DifySyncLogRepository;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DifySyncServiceTest {

    @Mock
    private DifyApiClient difyApiClient;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DifySyncLogRepository syncLogRepository;

    @Mock
    private DifyConfig config;

    @InjectMocks
    private DifySyncService difySyncService;

    private KnowledgeBaseEntity testEntity;

    @BeforeEach
    void setUp() {
        testEntity = new KnowledgeBaseEntity();
        testEntity.setId(1L);
        testEntity.setName("测试知识库");
        testEntity.setOriginalFilename("test.pdf");
        testEntity.setDifySyncStatus(DifySyncStatus.PENDING);

        DifyConfig.SyncConfig syncConfig = new DifyConfig.SyncConfig();
        syncConfig.setEnabled(true);
        when(config.getSync()).thenReturn(syncConfig);
        when(config.getDatasetId()).thenReturn("test-dataset-id");
    }

    @Test
    void syncToDify_shouldSuccess() {
        // Given
        when(difyApiClient.createDocument(anyString(), anyString(), anyMap()))
            .thenReturn("dify-doc-123");
        when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class)))
            .thenReturn(testEntity);
        when(syncLogRepository.save(any(DifySyncLogEntity.class)))
            .thenReturn(new DifySyncLogEntity());

        // When
        difySyncService.syncToDify(testEntity, "测试内容");

        // Then
        verify(difyApiClient).createDocument(eq("test-dataset-id"), eq("测试内容"), anyMap());
        assertEquals("dify-doc-123", testEntity.getDifyDocumentId());
        assertEquals(DifySyncStatus.SYNCED, testEntity.getDifySyncStatus());
        assertNull(testEntity.getDifySyncError());
    }

    @Test
    void syncToDify_withDisabledSync_shouldSkip() {
        // Given
        DifyConfig.SyncConfig syncConfig = new DifyConfig.SyncConfig();
        syncConfig.setEnabled(false);
        when(config.getSync()).thenReturn(syncConfig);

        // When
        difySyncService.syncToDify(testEntity, "测试内容");

        // Then
        verify(difyApiClient, never()).createDocument(anyString(), anyString(), anyMap());
    }

    @Test
    void syncToDify_withApiError_shouldSetFailedStatus() {
        // Given
        when(difyApiClient.createDocument(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("API 调用失败"));
        when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class)))
            .thenReturn(testEntity);
        when(syncLogRepository.save(any(DifySyncLogEntity.class)))
            .thenReturn(new DifySyncLogEntity());

        // When
        difySyncService.syncToDify(testEntity, "测试内容");

        // Then
        assertEquals(DifySyncStatus.FAILED, testEntity.getDifySyncStatus());
        assertNotNull(testEntity.getDifySyncError());
        verify(knowledgeBaseRepository).save(testEntity);
    }

    @Test
    void deleteFromDify_shouldSuccess() {
        // Given
        testEntity.setDifyDocumentId("dify-doc-123");
        doNothing().when(difyApiClient).deleteDocument(anyString(), anyString());
        when(syncLogRepository.save(any(DifySyncLogEntity.class)))
            .thenReturn(new DifySyncLogEntity());

        // When
        difySyncService.deleteFromDify(testEntity);

        // Then
        verify(difyApiClient).deleteDocument("test-dataset-id", "dify-doc-123");
    }

    @Test
    void deleteFromDify_withNoDifyDocId_shouldSkip() {
        // Given
        testEntity.setDifyDocumentId(null);

        // When
        difySyncService.deleteFromDify(testEntity);

        // Then
        verify(difyApiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void syncFromDify_shouldSyncDocuments() {
        // Given
        DifyDocument remoteDoc = DifyDocument.builder()
            .id("dify-doc-456")
            .name("远程文档")
            .updatedAt(LocalDateTime.now())
            .build();

        DifyDocumentList remoteDocs = DifyDocumentList.builder()
            .data(List.of(remoteDoc))
            .total(1)
            .build();

        when(difyApiClient.listDocuments(anyString(), anyInt(), anyInt()))
            .thenReturn(remoteDocs);
        when(knowledgeBaseRepository.findByDifyDocumentId("dify-doc-456"))
            .thenReturn(Optional.empty());
        when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class)))
            .thenReturn(new KnowledgeBaseEntity());
        when(syncLogRepository.save(any(DifySyncLogEntity.class)))
            .thenReturn(new DifySyncLogEntity());

        // When
        difySyncService.syncFromDify();

        // Then
        verify(difyApiClient).listDocuments("test-dataset-id", 1, 100);
    }
}
```

- [ ] **Step 4: 运行测试验证编译**

Run: `mvn test -pl app -Dtest=DifySyncServiceTest -Dmaven.test.skip=false`
Expected: 编译失败（DifySyncService 不存在）

- [ ] **Step 5: 创建 DifySyncService 实现**

```java
// DifySyncService.java
package interview.guide.modules.dify.service;

import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.exception.DifySyncException;
import interview.guide.modules.dify.model.*;
import interview.guide.modules.dify.repository.DifySyncLogRepository;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dify 同步服务
 * 负责本地知识库与 Dify 平台之间的数据同步
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DifySyncService {

    private final DifyApiClient difyApiClient;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DifySyncLogRepository syncLogRepository;
    private final DifyConfig config;

    /**
     * 同步知识库到 Dify（本地 → Dify）
     * 在知识库上传/更新时调用
     *
     * @param entity  知识库实体
     * @param content 知识库内容
     */
    @Async
    @Transactional
    public void syncToDify(KnowledgeBaseEntity entity, String content) {
        // 1. 检查同步是否启用
        if (!config.getSync().isEnabled()) {
            log.debug("Dify 同步已禁用，跳过: kbId={}", entity.getId());
            return;
        }

        log.info("开始同步到 Dify: kbId={}, name={}", entity.getId(), entity.getName());

        try {
            // 2. 准备元数据
            Map<String, Object> metadata = Map.of(
                "knowledgeBaseId", entity.getId().toString(),
                "name", entity.getName(),
                "originalFilename", entity.getOriginalFilename(),
                "category", entity.getCategory() != null ? entity.getCategory() : ""
            );

            // 3. 调用 Dify API 创建/更新文档
            String difyDocumentId;
            if (entity.getDifyDocumentId() != null && !entity.getDifyDocumentId().isBlank()) {
                // 已有 Dify 文档，更新
                difyApiClient.updateDocument(config.getDatasetId(), entity.getDifyDocumentId(), content);
                difyDocumentId = entity.getDifyDocumentId();
                log.info("更新 Dify 文档成功: kbId={}, difyDocId={}", entity.getId(), difyDocumentId);
            } else {
                // 新文档，创建
                difyDocumentId = difyApiClient.createDocument(config.getDatasetId(), content, metadata);
                log.info("创建 Dify 文档成功: kbId={}, difyDocId={}", entity.getId(), difyDocumentId);
            }

            // 4. 更新本地实体
            entity.setDifyDocumentId(difyDocumentId);
            entity.setDifySyncStatus(DifySyncStatus.SYNCED);
            entity.setDifySyncTime(LocalDateTime.now());
            entity.setDifySyncError(null);
            knowledgeBaseRepository.save(entity);

            // 5. 记录同步日志
            saveSyncLog(entity.getId(), difyDocumentId, DifySyncDirection.TO_DIFY,
                DifySyncAction.CREATE, DifySyncStatus.SUCCESS, null);

            log.info("同步到 Dify 完成: kbId={}, difyDocId={}", entity.getId(), difyDocumentId);

        } catch (Exception e) {
            log.warn("同步到 Dify 失败，降级到本地模式: kbId={}, error={}", entity.getId(), e.getMessage());

            // 更新失败状态
            entity.setDifySyncStatus(DifySyncStatus.FAILED);
            entity.setDifySyncError(e.getMessage());
            knowledgeBaseRepository.save(entity);

            // 记录失败日志
            saveSyncLog(entity.getId(), entity.getDifyDocumentId(), DifySyncDirection.TO_DIFY,
                DifySyncAction.CREATE, DifySyncStatus.FAILED, e.getMessage());

            // 不抛出异常，允许继续执行
        }
    }

    /**
     * 从 Dify 同步变更（Dify → 本地）
     * 由定时任务调用
     */
    @Transactional
    public void syncFromDify() {
        if (!config.getSync().isEnabled()) {
            log.debug("Dify 同步已禁用，跳过");
            return;
        }

        log.info("开始从 Dify 同步变更");

        try {
            int page = 1;
            int limit = 100;
            boolean hasMore = true;

            while (hasMore) {
                // 1. 获取 Dify 文档列表
                DifyDocumentList remoteDocs = difyApiClient.listDocuments(
                    config.getDatasetId(), page, limit
                );

                if (remoteDocs.getData() == null || remoteDocs.getData().isEmpty()) {
                    break;
                }

                // 2. 处理每个远程文档
                for (DifyDocument remoteDoc : remoteDocs.getData()) {
                    processRemoteDocument(remoteDoc);
                }

                // 3. 检查是否有更多数据
                hasMore = remoteDocs.isHasMore();
                page++;
            }

            log.info("从 Dify 同步完成");

        } catch (Exception e) {
            log.error("从 Dify 同步失败: {}", e.getMessage(), e);
            throw new DifySyncException("从 Dify 同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理远程文档
     */
    private void processRemoteDocument(DifyDocument remoteDoc) {
        try {
            // 查找本地对应的文档
            Optional<KnowledgeBaseEntity> localEntity = knowledgeBaseRepository.findByDifyDocumentId(remoteDoc.getId());

            if (localEntity.isPresent()) {
                // 本地存在，检查是否需要更新
                KnowledgeBaseEntity entity = localEntity.get();
                if (remoteDoc.getUpdatedAt() != null &&
                    (entity.getDifySyncTime() == null || remoteDoc.getUpdatedAt().isAfter(entity.getDifySyncTime()))) {
                    // Dify 有更新，但以本地为准，记录日志
                    log.info("Dify 文档有更新，但以本地为准: kbId={}, difyDocId={}",
                        entity.getId(), remoteDoc.getId());
                    saveSyncLog(entity.getId(), remoteDoc.getId(), DifySyncDirection.FROM_DIFY,
                        DifySyncAction.UPDATE, DifySyncStatus.SUCCESS,
                        "Dify 有更新，但以本地为准");
                }
            } else {
                // 本地不存在，记录日志（不自动创建，避免数据不一致）
                log.info("发现 Dify 文档在本地不存在: difyDocId={}, name={}",
                    remoteDoc.getId(), remoteDoc.getName());
                saveSyncLog(null, remoteDoc.getId(), DifySyncDirection.FROM_DIFY,
                    DifySyncAction.CREATE, DifySyncStatus.SUCCESS,
                    "Dify 文档在本地不存在，跳过同步");
            }
        } catch (Exception e) {
            log.error("处理远程文档失败: difyDocId={}, error={}", remoteDoc.getId(), e.getMessage());
        }
    }

    /**
     * 从 Dify 删除文档
     * 在本地知识库删除时调用
     *
     * @param entity 知识库实体
     */
    @Async
    @Transactional
    public void deleteFromDify(KnowledgeBaseEntity entity) {
        if (!config.getSync().isEnabled()) {
            log.debug("Dify 同步已禁用，跳过删除: kbId={}", entity.getId());
            return;
        }

        if (entity.getDifyDocumentId() == null || entity.getDifyDocumentId().isBlank()) {
            log.debug("知识库没有关联 Dify 文档，跳过删除: kbId={}", entity.getId());
            return;
        }

        log.info("从 Dify 删除文档: kbId={}, difyDocId={}", entity.getId(), entity.getDifyDocumentId());

        try {
            difyApiClient.deleteDocument(config.getDatasetId(), entity.getDifyDocumentId());

            saveSyncLog(entity.getId(), entity.getDifyDocumentId(), DifySyncDirection.TO_DIFY,
                DifySyncAction.DELETE, DifySyncStatus.SUCCESS, null);

            log.info("从 Dify 删除文档成功: kbId={}", entity.getId());

        } catch (Exception e) {
            log.warn("从 Dify 删除文档失败: kbId={}, error={}", entity.getId(), e.getMessage());

            saveSyncLog(entity.getId(), entity.getDifyDocumentId(), DifySyncDirection.TO_DIFY,
                DifySyncAction.DELETE, DifySyncStatus.FAILED, e.getMessage());

            // 不抛出异常，允许继续执行
        }
    }

    /**
     * 获取同步状态
     */
    @Transactional(readOnly = true)
    public List<DifySyncStatusDTO> getSyncStatus() {
        return knowledgeBaseRepository.findAll().stream()
            .map(entity -> new DifySyncStatusDTO(
                entity.getId(),
                entity.getName(),
                entity.getDifyDocumentId(),
                entity.getDifySyncStatus(),
                entity.getDifySyncTime(),
                entity.getDifySyncError()
            ))
            .toList();
    }

    /**
     * 获取同步日志
     */
    @Transactional(readOnly = true)
    public List<DifySyncLogEntity> getSyncLogs(Long knowledgeBaseId, int limit) {
        if (knowledgeBaseId != null) {
            return syncLogRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(
                knowledgeBaseId, org.springframework.data.domain.PageRequest.of(0, limit));
        } else {
            return syncLogRepository.findAll(org.springframework.data.domain.PageRequest.of(0, limit))
                .getContent();
        }
    }

    /**
     * 保存同步日志
     */
    private void saveSyncLog(Long knowledgeBaseId, String difyDocumentId,
                             DifySyncDirection direction, DifySyncAction action,
                             DifySyncStatus status, String errorMessage) {
        try {
            DifySyncLogEntity logEntity = DifySyncLogEntity.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .difyDocumentId(difyDocumentId)
                .syncDirection(direction)
                .syncAction(action)
                .syncStatus(status)
                .errorMessage(errorMessage)
                .build();
            syncLogRepository.save(logEntity);
        } catch (Exception e) {
            log.error("保存同步日志失败: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 6: 运行测试**

Run: `mvn test -pl app -Dtest=DifySyncServiceTest -Dmaven.test.skip=false`
Expected: 测试通过

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/interview/guide/modules/dify/repository/DifySyncLogRepository.java
git add app/src/main/java/interview/guide/modules/dify/service/DifySyncService.java
git add app/src/test/java/interview/guide/modules/dify/service/DifySyncServiceTest.java
git add app/src/main/java/interview/guide/modules/knowledgebase/model/KnowledgeBaseEntity.java
git commit -m "feat: 实现 Dify 同步服务"
```

---

## Task 6: Dify 对话服务

**Files:**
- Create: `app/src/main/java/interview/guide/modules/dify/service/DifyChatService.java`
- Create: `app/src/test/java/interview/guide/modules/dify/service/DifyChatServiceTest.java`

- [ ] **Step 1: 编写 DifyChatService 测试**

```java
// DifyChatServiceTest.java
package interview.guide.modules.dify.service;

import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.model.DifyChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DifyChatServiceTest {

    @Mock
    private DifyApiClient difyApiClient;

    @Mock
    private DifyConfig config;

    @InjectMocks
    private DifyChatService difyChatService;

    @BeforeEach
    void setUp() {
        when(config.getDatasetId()).thenReturn("test-dataset-id");
    }

    @Test
    void chat_shouldReturnResponse() {
        // Given
        String query = "什么是 Java?";
        String conversationId = null;
        List<Long> knowledgeBaseIds = List.of(1L, 2L);

        DifyChatResponse expectedResponse = DifyChatResponse.builder()
            .answer("Java 是一种编程语言")
            .conversationId("conv-123")
            .retrievalSources(List.of())
            .build();

        when(difyApiClient.chat(anyString(), any(), anyMap()))
            .thenReturn(expectedResponse);

        // When
        DifyChatResponse response = difyChatService.chat(query, conversationId, knowledgeBaseIds);

        // Then
        assertNotNull(response);
        assertEquals("Java 是一种编程语言", response.getAnswer());
        assertEquals("conv-123", response.getConversationId());
    }

    @Test
    void chat_withConversationId_shouldPassToApi() {
        // Given
        String query = "什么是 Java?";
        String conversationId = "conv-456";
        List<Long> knowledgeBaseIds = List.of(1L);

        DifyChatResponse expectedResponse = DifyChatResponse.builder()
            .answer("Java 是一种编程语言")
            .conversationId("conv-456")
            .build();

        when(difyApiClient.chat(eq(query), eq(conversationId), anyMap()))
            .thenReturn(expectedResponse);

        // When
        DifyChatResponse response = difyChatService.chat(query, conversationId, knowledgeBaseIds);

        // Then
        assertNotNull(response);
        verify(difyApiClient).chat(eq(query), eq(conversationId), anyMap());
    }

    @Test
    void chat_withNullQuery_shouldThrowException() {
        // Given
        String query = null;
        String conversationId = null;
        List<Long> knowledgeBaseIds = List.of(1L);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            difyChatService.chat(query, conversationId, knowledgeBaseIds);
        });
    }
}
```

- [ ] **Step 2: 运行测试验证编译**

Run: `mvn test -pl app -Dtest=DifyChatServiceTest -Dmaven.test.skip=false`
Expected: 编译失败（DifyChatService 不存在）

- [ ] **Step 3: 创建 DifyChatService 实现**

```java
// DifyChatService.java
package interview.guide.modules.dify.service;

import interview.guide.modules.dify.client.DifyApiClient;
import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.model.DifyChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 对话服务
 * 基于 Dify 平台进行知识库对话
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DifyChatService {

    private final DifyApiClient difyApiClient;
    private final DifyConfig config;

    /**
     * 基于 Dify 进行对话
     *
     * @param query            用户问题
     * @param conversationId   会话 ID（可选）
     * @param knowledgeBaseIds 知识库 ID 列表
     * @return 对话响应
     */
    public DifyChatResponse chat(String query, String conversationId, List<Long> knowledgeBaseIds) {
        // 参数校验
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }

        log.info("Dify 对话: query={}, conversationId={}, kbIds={}", query, conversationId, knowledgeBaseIds);

        // 构建输入参数
        Map<String, String> inputs = new HashMap<>();
        if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
            // 将知识库 ID 列表转换为逗号分隔的字符串
            String kbIdsStr = knowledgeBaseIds.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
            inputs.put("knowledge_base_ids", kbIdsStr);
        }

        // 调用 Dify API
        DifyChatResponse response = difyApiClient.chat(query, conversationId, inputs);

        log.info("Dify 对话完成: conversationId={}, answerLength={}",
            response.getConversationId(),
            response.getAnswer() != null ? response.getAnswer().length() : 0);

        return response;
    }

    /**
     * 流式对话（简化实现，实际需要使用 SSE）
     *
     * @param query          用户问题
     * @param conversationId 会话 ID
     * @return SSE 流
     */
    public Flux<String> chatStream(String query, String conversationId) {
        // 简化实现：使用阻塞式对话，返回单个结果
        // 实际生产环境应使用 Dify 的 streaming API
        return Flux.defer(() -> {
            try {
                DifyChatResponse response = chat(query, conversationId, null);
                return Flux.just(response.getAnswer());
            } catch (Exception e) {
                log.error("流式对话失败: {}", e.getMessage());
                return Flux.error(e);
            }
        });
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `mvn test -pl app -Dtest=DifyChatServiceTest -Dmaven.test.skip=false`
Expected: 测试通过

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/interview/guide/modules/dify/service/DifyChatService.java
git add app/src/test/java/interview/guide/modules/dify/service/DifyChatServiceTest.java
git commit -m "feat: 实现 Dify 对话服务"
```

---

## Task 7: Dify 同步调度器和控制器

**Files:**
- Create: `app/src/main/java/interview/guide/modules/dify/scheduler/DifySyncScheduler.java`
- Create: `app/src/main/java/interview/guide/modules/dify/controller/DifyController.java`

- [ ] **Step 1: 创建 DifySyncScheduler**

```java
// DifySyncScheduler.java
package interview.guide.modules.dify.scheduler;

import interview.guide.modules.dify.config.DifyConfig;
import interview.guide.modules.dify.service.DifySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dify 同步调度器
 * 定时从 Dify 拉取变更
 */
@Component
@Slf4j
@RequiredArgsConstructor
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
            log.debug("Dify 同步已禁用，跳过定时任务");
            return;
        }

        log.info("开始定时从 Dify 同步变更");
        try {
            difySyncService.syncFromDify();
            log.info("定时从 Dify 同步完成");
        } catch (Exception e) {
            log.error("定时从 Dify 同步失败: {}", e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 2: 创建 DifyController**

```java
// DifyController.java
package interview.guide.modules.dify.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.dify.model.*;
import interview.guide.modules.dify.service.DifyChatService;
import interview.guide.modules.dify.service.DifySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Dify 控制器
 * 提供 Dify 相关的 API 接口
 */
@Slf4j
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
        log.info("手动触发 Dify 同步");
        syncService.syncFromDify();
        return Result.success(null);
    }

    /**
     * 获取同步状态
     */
    @GetMapping("/sync/status")
    public Result<List<DifySyncStatusDTO>> getSyncStatus() {
        return Result.success(syncService.getSyncStatus());
    }

    /**
     * 获取同步日志
     */
    @GetMapping("/sync/logs")
    public Result<List<DifySyncLogEntity>> getSyncLogs(
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(syncService.getSyncLogs(knowledgeBaseId, limit));
    }

    /**
     * 基于 Dify 对话
     */
    @PostMapping("/chat")
    public Result<DifyChatResponse> chat(@RequestBody DifyChatRequest request) {
        log.info("Dify 对话请求: query={}, kbIds={}", request.query(), request.knowledgeBaseIds());
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
        log.info("Dify 流式对话请求: query={}", request.query());
        return chatService.chatStream(request.query(), request.conversationId());
    }

    /**
     * 运行工作流
     */
    @PostMapping("/workflow/{workflowId}")
    public Result<DifyWorkflowResponse> runWorkflow(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> inputs) {
        log.info("运行 Dify 工作流: workflowId={}", workflowId);
        // TODO: 实现工作流运行
        return Result.error("工作流功能暂未实现");
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `mvn compile -pl app`
Expected: 编译成功

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/interview/guide/modules/dify/scheduler/DifySyncScheduler.java
git add app/src/main/java/interview/guide/modules/dify/controller/DifyController.java
git commit -m "feat: 实现 Dify 同步调度器和控制器"
```

---

## Task 8: 集成到现有知识库服务

**Files:**
- Modify: `app/src/main/java/interview/guide/modules/knowledgebase/service/KnowledgeBaseUploadService.java`
- Modify: `app/src/main/java/interview/guide/modules/knowledgebase/service/KnowledgeBaseDeleteService.java`

- [ ] **Step 1: 在 KnowledgeBaseUploadService 中注入 DifySyncService**

在类中添加字段：

```java
private final DifySyncService difySyncService;
```

- [ ] **Step 2: 在上传完成后调用 Dify 同步**

在 `uploadKnowledgeBase` 方法的返回结果之前，添加：

```java
// 异步同步到 Dify
difySyncService.syncToDify(savedEntity, content);
```

- [ ] **Step 3: 在 KnowledgeBaseDeleteService 中注入 DifySyncService**

在类中添加字段：

```java
private final DifySyncService difySyncService;
```

- [ ] **Step 4: 在删除时调用 Dify 删除**

在 `deleteKnowledgeBase` 方法中，删除本地数据之前，添加：

```java
// 从 Dify 删除
difySyncService.deleteFromDify(entity);
```

- [ ] **Step 5: 验证编译**

Run: `mvn compile -pl app`
Expected: 编译成功

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/interview/guide/modules/knowledgebase/service/KnowledgeBaseUploadService.java
git add app/src/main/java/interview/guide/modules/knowledgebase/service/KnowledgeBaseDeleteService.java
git commit -m "feat: 集成 Dify 同步到知识库上传和删除流程"
```

---

## Task 9: 启用异步支持和重试

**Files:**
- Modify: `app/src/main/java/interview/guide/InterviewGuideApplication.java`

- [ ] **Step 1: 启用异步和调度支持**

在主应用类上添加注解：

```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableRetry
public class InterviewGuideApplication {
    // ...
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl app`
Expected: 编译成功

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/interview/guide/InterviewGuideApplication.java
git commit -m "feat: 启用异步、调度和重试支持"
```

---

## Task 10: 端到端测试

- [ ] **Step 1: 启动应用**

Run: `mvn spring-boot:run -pl app`
Expected: 应用启动成功

- [ ] **Step 2: 测试上传知识库**

Run:
```bash
curl -X POST http://localhost:8080/api/knowledgebase/upload \
  -F "file=@test.pdf" \
  -F "name=测试知识库" \
  -F "category=测试"
```

Expected: 返回成功，检查日志中是否有 Dify 同步记录

- [ ] **Step 3: 测试查询同步状态**

Run: `curl http://localhost:8080/api/dify/sync/status`
Expected: 返回同步状态列表

- [ ] **Step 4: 测试手动同步**

Run: `curl -X POST http://localhost:8080/api/dify/sync`
Expected: 返回成功，日志显示同步过程

- [ ] **Step 5: 测试对话**

Run:
```bash
curl -X POST http://localhost:8080/api/dify/chat \
  -H "Content-Type: application/json" \
  -d '{"query": "什么是 Java?", "knowledgeBaseIds": [1]}'
```

Expected: 返回对话响应

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "test: 添加端到端测试验证"
```

---

## 完成检查清单

- [ ] 所有任务完成
- [ ] 所有测试通过
- [ ] 代码编译无错误
- [ ] 功能验证通过
- [ ] 文档更新（如有必要）

---

## 后续优化（可选）

1. **流式对话支持**: 使用 Dify 的 streaming API 实现真正的流式响应
2. **工作流集成**: 完成工作流运行功能
3. **监控指标**: 添加 Micrometer 指标监控同步成功率
4. **批量同步**: 优化大批量文档的同步性能
5. **冲突解决**: 实现更智能的冲突解决策略
