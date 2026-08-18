package aigc.gameflow.exception;

/** 请求与当前资源状态冲突，例如重复幂等键或非法状态迁移。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
