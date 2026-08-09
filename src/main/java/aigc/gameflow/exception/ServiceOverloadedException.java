package aigc.gameflow.exception;

public class ServiceOverloadedException extends RuntimeException {
    public ServiceOverloadedException(String message) {
        super(message);
    }
}
