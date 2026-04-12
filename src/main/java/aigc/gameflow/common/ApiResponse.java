package aigc.gameflow.common;

public class ApiResponse<T> {

    private final int code;
    private final String msg;
    private final T data;

    private ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(200, msg, data);
    }

    public static ApiResponse<Void> success(String msg) {
        return new ApiResponse<>(200, msg, null);
    }

    public static ApiResponse<Void> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}
