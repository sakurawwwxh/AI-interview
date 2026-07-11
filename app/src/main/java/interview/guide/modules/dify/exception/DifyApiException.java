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
