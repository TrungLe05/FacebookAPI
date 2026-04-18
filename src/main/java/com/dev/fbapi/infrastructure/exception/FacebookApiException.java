package com.dev.fbapi.infrastructure.exception;

public class FacebookApiException extends RuntimeException {
    public FacebookApiException(String message) {
        super(message);
    }
}
