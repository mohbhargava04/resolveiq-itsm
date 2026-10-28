package com.resolveiq.common.exception;

/** The incident data violates a domain invariant (e.g. empty description, unknown category). */
public class InvalidIncidentException extends ItsmException {

    public InvalidIncidentException(String message) {
        super(ErrorCode.INVALID_INCIDENT, message);
    }
}
