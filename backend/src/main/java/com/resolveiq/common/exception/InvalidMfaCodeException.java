package com.resolveiq.common.exception;

public class InvalidMfaCodeException extends ItsmException {

    public InvalidMfaCodeException() {
        super(ErrorCode.INVALID_MFA_CODE, "That code is not valid. Check your authenticator app and try again.");
    }
}
