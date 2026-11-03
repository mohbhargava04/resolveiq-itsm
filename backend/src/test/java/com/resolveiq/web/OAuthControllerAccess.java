package com.resolveiq.web;

/** Exposes package-private helpers of the web layer to tests in other packages. */
public final class OAuthControllerAccess {

    private OAuthControllerAccess() {
    }

    public static String safeReturn(String value) {
        return OAuthController.safeReturn(value);
    }
}
