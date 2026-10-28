package com.resolveiq.common.exception;

/** Thrown when a requested entity does not exist. Subclasses give more specific codes. */
public class ResourceNotFoundException extends ItsmException {

    public ResourceNotFoundException(String resource, Object identifier) {
        this(ErrorCode.RESOURCE_NOT_FOUND, resource + " '" + identifier + "' was not found");
    }

    protected ResourceNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }
}
