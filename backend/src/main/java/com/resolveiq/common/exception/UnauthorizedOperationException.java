package com.resolveiq.common.exception;

/** The caller is authenticated but not allowed to perform this operation on this resource. */
public class UnauthorizedOperationException extends ItsmException {

    public UnauthorizedOperationException(String message) {
        super(ErrorCode.UNAUTHORIZED_OPERATION, message);
    }
}
