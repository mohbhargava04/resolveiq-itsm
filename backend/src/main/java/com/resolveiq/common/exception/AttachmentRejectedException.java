package com.resolveiq.common.exception;

public class AttachmentRejectedException extends ItsmException {

    public AttachmentRejectedException(String message) {
        super(ErrorCode.ATTACHMENT_REJECTED, message);
    }
}
