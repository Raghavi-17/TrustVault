package com.trustvault.common;

public class AccessForbiddenException extends RuntimeException {
    public AccessForbiddenException(String message) {
        super(message);
    }
}
