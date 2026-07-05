package com.mitra.common;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }

    public static ForbiddenException notYourResource(String resource) {
        return new ForbiddenException("You do not have access to this " + resource);
    }
}
