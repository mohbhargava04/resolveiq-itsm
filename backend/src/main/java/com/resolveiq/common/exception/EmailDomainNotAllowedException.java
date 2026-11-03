package com.resolveiq.common.exception;

public class EmailDomainNotAllowedException extends ItsmException {

    public EmailDomainNotAllowedException(String message) {
        super(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED, message);
    }
}
