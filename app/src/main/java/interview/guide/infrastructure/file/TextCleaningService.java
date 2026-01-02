package interview.guide.infrastructure.file;

import org.springframework.stereotype.Service;

/**
 * 文本清理服务
 * 提供统一的文本内容清理和规范化功能
 */
@Service
public class TextCleaningService {

    /**
     * 清理和规范化文本内容
     * - 规范化换行符
     * - 去除连续多个空行
     * - 去除行首行尾多余空格
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    public String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text
            // 规范化换行符
            .replaceAll("\\r\\n", "\n")
            .replaceAll("\\r", "\n")
            // 去除连续多个空行，保留最多两个
            .replaceAll("\\n{3,}", "\n\n")
            // 去除行首行尾多余空格
            .lines()
            .map(String::strip)
            .reduce((a, b) -> a + "\n" + b)
            .orElse("")
            .strip();
    }

    /**
     * 清理文本并限制最大长度
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @return 清理后的文本（可能被截断）
     */
    public String cleanTextWithLimit(String text, int maxLength) {
        String cleaned = cleanText(text);
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    /**
     * 清理文本并移除所有换行符（转为空格）
     * 适用于需要单行显示的场景
     *
     * @param text 原始文本
     * @return 单行文本
     */
    public String cleanToSingleLine(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text
            .replaceAll("\\r\\n", " ")
            .replaceAll("\\r", " ")
            .replaceAll("\\n", " ")
            .replaceAll("\\s+", " ")
            .strip();
    }

    /**
     * 移除 HTML 标签
     *
     * @param text 可能包含 HTML 的文本
     * @return 纯文本
     */
    public String stripHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text
            .replaceAll("<[^>]+>", " ")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&quot;", "\"")
            .replaceAll("\\s+", " ")
            .strip();
    }
}
