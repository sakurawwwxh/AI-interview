package ai.interview.modules.dify.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 管理员调整本地知识库归属时的请求体。 */
public record DifyKnowledgeBaseOwnerRequest(
    @NotNull(message = "归属用户不能为空")
    @Positive(message = "归属用户无效")
    Long userId
) {}
