package com.resolveiq.common.exception;

public class SessionExpiredException extends ItsmException {

    public SessionExpiredException() {
        super(ErrorCode.SESSION_EXPIRED, "Your session has ended. Please sign in again.");
    }
}
