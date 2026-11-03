package com.resolveiq.common.exception;

public class InvalidTokenException extends ItsmException {

    public InvalidTokenException(String message) {
        super(ErrorCode.INVALID_TOKEN, message);
    }
}
