package com.example.ctf.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), errorCode.status());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, errorCode.status());
    }

    public BusinessException(ErrorCode errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
