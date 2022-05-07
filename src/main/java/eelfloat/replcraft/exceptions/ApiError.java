package eelfloat.replcraft.exceptions;

public class ApiError extends Exception {
    public String type;
    public String message;

    public static final ApiError OFFLINE = new ApiError("offline", "Due to this server's configuration, this API call requires you to be online.");

    public ApiError(String type, String message) {
        super(type + ": " + message);
        this.type = type;
        this.message = message;
    }
}
