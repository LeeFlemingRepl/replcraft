package eelfloat.replcraft.exceptions;

import io.jsonwebtoken.JwtException;

public class InvalidStructure extends ApiError {
    public InvalidStructure(String msg) {
        super("invalid structure", msg);
    }

    public InvalidStructure(String msg, JwtException cause) {
        super("invalid structure", msg, cause);
    }
}
