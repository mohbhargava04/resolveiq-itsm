package com.resolveiq.common.exception;

/** No eligible technician exists, or the chosen technician is off duty / inactive. */
public class TechnicianUnavailableException extends ItsmException {

    public TechnicianUnavailableException(String message) {
        super(ErrorCode.TECHNICIAN_UNAVAILABLE, message);
    }
}
