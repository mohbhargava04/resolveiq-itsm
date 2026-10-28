package com.resolveiq.common.exception;

/** Failure of a third-party integration (SMTP server, SMS provider, AI API, ...). */
public class ExternalServiceException extends ItsmException {

    public ExternalServiceException(String message, Throwable cause) {
        super(ErrorCode.EXTERNAL_SERVICE_FAILURE, message, cause);
    }
}
