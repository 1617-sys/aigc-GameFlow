package aigc.gameflow.exception;

/** 全局队列积压达到阈值，暂时拒绝新任务。 */
public class ServiceOverloadedException extends RuntimeException {
    public ServiceOverloadedException(String message) {
        super(message);
    }
}
