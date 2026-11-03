package com.resolveiq.security.session;

/** How a session was started; shown on the "where you're signed in" list and in the audit log. */
public enum SignInMethod {
    PASSWORD("Password"),
    PASSWORD_MFA("Password + 2FA"),
    GOOGLE("Google"),
    GOOGLE_MFA("Google + 2FA");

    private final String label;

    SignInMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public SignInMethod withMfa() {
        return switch (this) {
            case PASSWORD, PASSWORD_MFA -> PASSWORD_MFA;
            case GOOGLE, GOOGLE_MFA -> GOOGLE_MFA;
        };
    }
}
