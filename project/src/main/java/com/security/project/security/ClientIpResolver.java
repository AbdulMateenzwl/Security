package com.security.project.security;

import java.util.List;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.security.project.config.properties.AppSecurityProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP for per-IP rate limiting and session audit records.
 *
 * <p>{@code X-Forwarded-For} is attacker-controlled, so it is only honoured when the direct peer
 * (the TCP socket address) is a <em>configured trusted proxy</em>. Trusted proxies may be given as
 * single IPs or CIDR ranges (e.g. the private subnet the reverse proxy sits on) — needed because a
 * container/ingress address is not fixed.</p>
 *
 * <p>When trusted, the header is parsed <b>right-to-left</b>: the rightmost entry is the nearest
 * hop, and each trusted-proxy hop is skipped until the first non-trusted address is found — that is
 * the real client. Because a client can only prepend forged entries on the <em>left</em>, a forged
 * value always sits further left than the genuine client hop and is never selected. With no trusted
 * proxies configured (the default), the socket address is always used, so a client cannot forge its
 * IP to dodge login/registration throttling.</p>
 */
@Component
public class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public ClientIpResolver(AppSecurityProperties props) {
        List<String> configured = props.trustedProxies() == null ? List.of() : props.trustedProxies();
        this.trustedProxyMatchers = configured.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        // Only trust X-Forwarded-For if the request actually reached us via one of our proxies.
        if (!isTrustedProxy(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwarded)) {
            return remote;
        }
        // Walk hops from nearest (right) to furthest (left); the first non-proxy hop is the client.
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !isTrustedProxy(hop)) {
                return hop;
            }
        }
        return remote;
    }

    private boolean isTrustedProxy(String ip) {
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }
}
