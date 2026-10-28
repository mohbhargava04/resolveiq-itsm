package com.resolveiq.common.exception;

/** Generic violation of a documented business rule (see docs/requirements.md). */
public class BusinessRuleViolationException extends ItsmException {

    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
