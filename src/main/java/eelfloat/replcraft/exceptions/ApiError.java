package eelfloat.replcraft.exceptions;

public class ApiError extends Exception {
    public String type;
    public String message;

    /** A request that cannot be completed due to requiring the player to be online */
    public static final ApiError OFFLINE = new ApiError("offline", "Due to this server's configuration, this API call requires you to be online.");

    /**
     * A request that cannot be completed due to being invalid or applied to invalid state.
     * This generally cannot be fixed from code and requires player intervention.
     */
    public static final String BAD_REQUEST = "bad request";

    /**
     * A request that cannot be completed due to being applied to an invalid location.
     * Fixable by applying the request to a proper location, or changing the current location to be proper.
     */
    public static final String INVALID_OPERATION = "invalid operation";

    /** A request that cannot be completed due to not being authenticated, or using an invalid context */
    public static final String UNAUTHENTICATED = "unauthenticated";

    public static final String AUTHENTICATION_FAILED = "authentication failed";

    /** A request that cannot be completed due to being out of fuel */
    public static final String OUT_OF_FUEL = "out of fuel";

    public ApiError(String type, String message) {
        this(type, message, null);
    }
    public ApiError(String type, String message, Exception cause) {
        super(type + ": " + message, cause);
        this.type = type;
        this.message = message;
    }
}
