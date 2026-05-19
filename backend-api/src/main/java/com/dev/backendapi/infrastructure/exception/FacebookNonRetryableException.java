package com.dev.backendapi.infrastructure.exception;

import lombok.Getter;

@Getter
public class FacebookNonRetryableException extends RuntimeException {

    private final int code;
    private final int subcode;

    public FacebookNonRetryableException(String message, int code, int subcode) {
        super(message);
        this.code = code;
        this.subcode = subcode;
    }
}
