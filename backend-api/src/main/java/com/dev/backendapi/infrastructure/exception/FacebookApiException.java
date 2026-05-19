package com.dev.backendapi.infrastructure.exception;

public class FacebookApiException extends RuntimeException {
    public FacebookApiException(String message) {
        super(message);
    }
}
