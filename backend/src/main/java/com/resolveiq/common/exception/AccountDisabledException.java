package com.resolveiq.common.exception;

public class AccountDisabledException extends ItsmException {

    public AccountDisabledException() {
        super(ErrorCode.ACCOUNT_DISABLED, "This account has been deactivated by an administrator");
    }
}
