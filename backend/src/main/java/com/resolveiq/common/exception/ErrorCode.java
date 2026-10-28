package com.resolveiq.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to API clients. The frontend switches on these
 * codes rather than on human-readable messages.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Invalid input"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid login"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account locked"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account disabled"),
    UNAUTHORIZED_OPERATION(HttpStatus.FORBIDDEN, "Unauthorized operation"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    INCIDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Incident not found"),
    SERVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "IT service not found"),
    INVALID_INCIDENT(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid incident"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition"),
    TECHNICIAN_UNAVAILABLE(HttpStatus.CONFLICT, "Technician unavailable"),
    WORKLOAD_EXCEEDED(HttpStatus.CONFLICT, "Technician workload exceeded"),
    MISSING_SLA_CONFIGURATION(HttpStatus.UNPROCESSABLE_ENTITY, "Missing SLA configuration"),
    DUPLICATE_INCIDENT(HttpStatus.CONFLICT, "Duplicate incident"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Duplicate resource"),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable"),
    EXTERNAL_SERVICE_FAILURE(HttpStatus.BAD_GATEWAY, "External service failure"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
