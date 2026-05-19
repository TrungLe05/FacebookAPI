package com.dev.coreservice.client;

/**
 * Exception cho các lỗi Facebook API không nên retry:
 * - Token hết hạn (error_subcode 463)
 * - Token invalid (error code 190)
 * - Comment đã xóa / không tồn tại
 */
public class FacebookNonRetryableException extends RuntimeException {
    private final int errorCode;
    private final int errorSubcode;

    public FacebookNonRetryableException(String message, int errorCode, int errorSubcode) {
        super(message);
        this.errorCode = errorCode;
        this.errorSubcode = errorSubcode;
    }

    public int getErrorCode() { return errorCode; }
    public int getErrorSubcode() { return errorSubcode; }
}
