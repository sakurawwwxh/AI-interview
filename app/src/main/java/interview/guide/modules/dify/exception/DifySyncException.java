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
