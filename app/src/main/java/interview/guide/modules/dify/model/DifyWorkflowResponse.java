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
