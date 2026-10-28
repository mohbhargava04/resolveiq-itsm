package com.resolveiq.common.exception;

/** A unique business key (e-mail, service code, category code) is already taken. */
public class DuplicateResourceException extends ItsmException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
