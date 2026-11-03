package com.resolveiq.common.exception;

public class RegistrationDisabledException extends ItsmException {

    public RegistrationDisabledException(String message) {
        super(ErrorCode.REGISTRATION_DISABLED, message);
    }
}
