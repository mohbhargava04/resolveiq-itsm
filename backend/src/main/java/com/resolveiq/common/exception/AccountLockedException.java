package com.resolveiq.common.exception;

import java.time.Instant;
import java.util.Map;

public class AccountLockedException extends ItsmException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super(ErrorCode.ACCOUNT_LOCKED,
                "Account temporarily locked after repeated failed logins. Try again later.");
        this.lockedUntil = lockedUntil;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("lockedUntil", lockedUntil.toString());
    }
}
