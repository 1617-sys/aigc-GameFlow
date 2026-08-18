package aigc.gameflow.exception;

/** 单个用户提交频率超过限制。 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
