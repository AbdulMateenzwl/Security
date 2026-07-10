package com.security.project.security;

import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/** Small helpers for extracting request metadata. */
public final class HttpRequestUtils {

    private HttpRequestUtils() {
    }

    /**
     * Best-effort client IP: honours {@code X-Forwarded-For} (first hop) behind a trusted proxy, else
     * the socket address. Used for per-IP rate limiting and session audit records.
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
