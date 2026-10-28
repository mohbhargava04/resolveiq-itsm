package com.resolveiq.common.exception;

import java.util.Map;

/**
 * Root of the application's checked-by-convention exception hierarchy. Every business failure is a
 * subclass carrying an {@link ErrorCode}, so one global handler can translate all of them into
 * consistent HTTP responses (RFC 7807 problem details) without a chain of instanceof checks.
 */
public abstract class ItsmException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ItsmException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ItsmException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Extra structured data for the client (e.g. the incidents a duplicate matched). */
    public Map<String, Object> details() {
        return Map.of();
    }
}
