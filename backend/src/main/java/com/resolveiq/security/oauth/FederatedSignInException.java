package com.resolveiq.security.oauth;

/**
 * A company-account sign-in could not be completed. {@code reason} is a short code the sign-in page
 * turns into a friendly message (the browser is redirected there, so no JSON error body is possible).
 */
public class FederatedSignInException extends RuntimeException {

    private final String reason;

    public FederatedSignInException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FederatedSignInException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
