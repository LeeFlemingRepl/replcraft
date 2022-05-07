package eelfloat.replcraft.exceptions;

import io.jsonwebtoken.JwtException;

public class InvalidStructure extends Exception {
    public InvalidStructure(String msg) {
        super(msg);
    }

    public InvalidStructure(String msg, JwtException cause) {
        super(msg, cause);
    }
}
