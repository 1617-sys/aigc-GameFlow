package aigc.gameflow.image;

public enum GenerationStatus {
    PENDING(0),
    RUNNING(1),
    SUCCESS(2),
    FAILED(3),
    CANCELED(4),
    RETRYING(5);

    private final int code;

    GenerationStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
