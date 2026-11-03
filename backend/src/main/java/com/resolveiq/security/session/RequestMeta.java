package com.resolveiq.security.session;

import jakarta.servlet.http.HttpServletRequest;

/** Who is calling: client address and browser, recorded with each session for the user to review. */
public record RequestMeta(String ipAddress, String userAgent) {

    public static final RequestMeta UNKNOWN = new RequestMeta(null, null);

    public static RequestMeta of(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return new RequestMeta(request.getRemoteAddr(), ua == null ? null : ua.substring(0, Math.min(ua.length(), 250)));
    }
}
