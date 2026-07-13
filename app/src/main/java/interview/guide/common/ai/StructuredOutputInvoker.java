package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 统一封装结构化输出调用与重试策略。
 *
 * <p>在每次 AI 调用前校验 token 配额，调用后记录 token 用量，
 * 由 {@link AiUsageRecorder} 负责统计和限额管理。
 */
@Component
public class StructuredOutputInvoker {

    private static final String STRICT_JSON_INSTRUCTION = """
请仅返回可被 JSON 解析器直接解析的 JSON 对象，并严格满足字段结构要求：
1) 不要输出 Markdown 代码块（如 ```json）。
2) 不要输出任何解释文字、前后缀、注释。
3) 所有字符串内引号必须正确转义。
""";

    private final int maxAttempts;
    private final boolean includeLastErrorInRetryPrompt;
    private final AiUsageRecorder usageRecorder;

    public StructuredOutputInvoker(
        @Value("${app.ai.structured-max-attempts:2}") int maxAttempts,
        @Value("${app.ai.structured-include-last-error:true}") boolean includeLastErrorInRetryPrompt,
        AiUsageRecorder usageRecorder
    ) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.includeLastErrorInRetryPrompt = includeLastErrorInRetryPrompt;
        this.usageRecorder = usageRecorder;
    }

    public <T> T invoke(
        ChatClient chatClient,
        String systemPromptWithFormat,
        String userPrompt,
        BeanOutputConverter<T> outputConverter,
        ErrorCode errorCode,
        String errorPrefix,
        String logContext,
        Logger log
    ) {
        // 调用前校验配额
        usageRecorder.checkQuota();

        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String attemptSystemPrompt = attempt == 1
                ? systemPromptWithFormat
                : buildRetrySystemPrompt(systemPromptWithFormat, lastError);
            try {
                // 获取完整 ChatResponse 以捕获 token 用量
                ChatResponse response = chatClient.prompt()
                    .system(attemptSystemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();

                // 记录 token 用量
                recordUsage(response, logContext, log);

                // 手动用 outputConverter 解析响应内容
                String content = response.getResult().getOutput().getText();
                return outputConverter.convert(content);
            } catch (BusinessException e) {
                // 业务异常（如配额超限）直接抛出，不重试
                throw e;
            } catch (Exception e) {
                lastError = e;
                log.warn("{}结构化解析失败，准备重试: attempt={}, error={}", logContext, attempt, e.getMessage());
            }
        }

        throw new BusinessException(
            errorCode,
            errorPrefix + (lastError != null ? lastError.getMessage() : "unknown")
        );
    }

    /**
     * 从 ChatResponse 中提取 Usage 并记录到 AiUsageRecorder
     */
    private void recordUsage(ChatResponse response, String logContext, Logger log) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        if (promptTokens > 0 || completionTokens > 0) {
            usageRecorder.recordUsage(promptTokens, completionTokens, logContext);
        }
    }

    private String buildRetrySystemPrompt(String systemPromptWithFormat, Exception lastError) {
        StringBuilder prompt = new StringBuilder(systemPromptWithFormat)
            .append("\n\n")
            .append(STRICT_JSON_INSTRUCTION)
            .append("\n上次输出解析失败，请仅返回合法 JSON。");

        if (includeLastErrorInRetryPrompt && lastError != null && lastError.getMessage() != null) {
            prompt.append("\n上次失败原因：")
                .append(sanitizeErrorMessage(lastError.getMessage()));
        }
        return prompt.toString();
    }

    private String sanitizeErrorMessage(String message) {
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 200) {
            return oneLine.substring(0, 200) + "...";
        }
        return oneLine;
    }
}
