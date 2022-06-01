package eelfloat.replcraft.exceptions;

public class ApiError extends Exception {
    public String type;
    public String message;

    public static final ApiError OFFLINE = new ApiError("offline", "Due to this server's configuration, this API call requires you to be online.");

    public ApiError(String type, String message) {
        this(type, message, null);
    }
    public ApiError(String type, String message, Exception cause) {
        super(type + ": " + message, cause);
        this.type = type;
        this.message = message;
    }
}
