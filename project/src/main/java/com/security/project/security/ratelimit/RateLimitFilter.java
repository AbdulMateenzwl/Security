package com.security.project.security.ratelimit;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.security.project.config.properties.RateLimitProperties;
import com.security.project.config.properties.RateLimitProperties.Limit;
import com.security.project.domain.user.entity.User;
import com.security.project.exception.ErrorResponse;
import com.security.project.security.HttpRequestUtils;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis-backed, per-endpoint rate limiting.
 *
 * <p>Runs after {@link com.security.project.security.jwt.JwtAuthenticationFilter} so the authenticated
 * user is available: authenticated endpoints are limited per user, while the public login/register
 * endpoints are limited per client IP. On exhaustion the request is rejected with {@code 429} and the
 * standard {@code Retry-After} / {@code X-RateLimit-*} headers — never leaking anything internal.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Fallback for any other authenticated {@code /api/**} endpoint: 200 requests/minute per user. */
    private static final Limit DEFAULT_LIMIT = new Limit(200, 200, 60);

    private final RateLimiterService rateLimiter;
    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterService rateLimiter,
                           RateLimitProperties props,
                           ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** A resolved limit for a request: a bucket name, its limit, and the caller identity to key on. */
    private record Rule(String name, Limit limit, String identity) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = resolve(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = rateLimiter.tryConsume(rule.name(), rule.identity(), rule.limit());

        response.setHeader("X-RateLimit-Limit", Integer.toString(rule.limit().capacity()));
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0);
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(),
                    ErrorResponse.of("RATE_LIMIT_EXCEEDED", "Too many requests — please retry later"));
        }
    }

    /** Map a request to its rate-limit rule, or {@code null} when it should not be limited. */
    private Rule resolve(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && "/api/auth/login".equals(uri)) {
            return new Rule("login", props.login(), HttpRequestUtils.clientIp(request));
        }
        if ("POST".equals(method) && "/api/auth/register".equals(uri)) {
            return new Rule("register", props.register(), HttpRequestUtils.clientIp(request));
        }

        UUID userId = currentUserId();
        if (userId == null) {
            return null;   // unauthenticated calls to protected routes are rejected by security anyway
        }
        if ("POST".equals(method) && uri.matches("/api/chats/[^/]+/messages")) {
            return new Rule("message-send", props.messageSend(), userId.toString());
        }
        if ("GET".equals(method) && uri.startsWith("/api/signal/pre-key-bundle/")) {
            return new Rule("pre-key-fetch", props.preKeyFetch(), userId.toString());
        }
        if (uri.startsWith("/api/")) {
            return new Rule("default", DEFAULT_LIMIT, userId.toString());
        }
        return null;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}
