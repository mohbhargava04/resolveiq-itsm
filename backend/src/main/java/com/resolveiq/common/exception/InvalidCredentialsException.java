package com.resolveiq.common.exception;

/**
 * Deliberately vague: the same message is used for an unknown e-mail and a wrong password so the
 * API cannot be used to discover which accounts exist.
 */
public class InvalidCredentialsException extends ItsmException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }
}
