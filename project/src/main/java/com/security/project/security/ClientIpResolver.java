package com.security.project.security;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.security.project.config.properties.AppSecurityProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP for per-IP rate limiting and session audit records.
 *
 * <p>{@code X-Forwarded-For} is attacker-controlled, so it is only honoured when the direct peer
 * (the TCP socket address) is a <em>configured trusted proxy</em>. With no trusted proxies configured
 * (the default), the socket address is always used — a client therefore cannot forge its IP to get a
 * fresh rate-limit bucket and bypass login/registration throttling.</p>
 */
@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(AppSecurityProperties props) {
        this.trustedProxies = props.trustedProxies() == null ? Set.of() : Set.copyOf(props.trustedProxies());
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.isEmpty() && trustedProxies.contains(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();   // first hop = original client, set by our proxy
            }
        }
        return remote;
    }
}
