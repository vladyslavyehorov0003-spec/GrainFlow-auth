package com.grainflow.auth.exception;

import org.springframework.http.HttpStatus;

// Base exception for all auth-related business errors
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    // 400
    public static AuthException badRequest(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }

    // 401
    public static AuthException unauthorized(String message) {
        return new AuthException(message, HttpStatus.UNAUTHORIZED);
    }

    // 403
    public static AuthException forbidden(String message) {
        return new AuthException(message, HttpStatus.FORBIDDEN);
    }

    // 404
    public static AuthException notFound(String message) {
        return new AuthException(message, HttpStatus.NOT_FOUND);
    }

    // 409
    public static AuthException conflict(String message) {
        return new AuthException(message, HttpStatus.CONFLICT);
    }

    // 501
    public static AuthException notImplemented(String message) {
        return new AuthException(message, HttpStatus.NOT_IMPLEMENTED);
    }
}
