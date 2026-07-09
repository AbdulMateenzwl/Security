package com.security.project.security.jwt;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.entity.UserSession;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.domain.user.repository.UserSessionRepository;
import com.security.project.exception.ApiException;
import com.security.project.exception.ErrorResponse;
import com.security.project.exception.InvalidTokenException;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Authenticates each request from a {@code Authorization: Bearer <jwt>} header.
 *
 * <p>Security decisions enforced here on every request:</p>
 * <ul>
 *   <li>Tokens are only accepted from the Authorization header — never cookies or query params.</li>
 *   <li>The token must be an <em>access</em> token (a refresh token is rejected).</li>
 *   <li>The token's session must still be active (not revoked, not expired) — enabling instant,
 *       server-side revocation.</li>
 *   <li>The backing user must still exist and be unlocked.</li>
 *   <li>The raw token value is never logged.</li>
 * </ul>
 *
 * <p>A missing token is left to the entry point (so public endpoints still work); a <em>present but
 * invalid</em> token is rejected immediately with 401.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   UserSessionRepository sessionRepository,
                                   UserRepository userRepository,
                                   ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            chain.doFilter(request, response);   // anonymous — entry point guards protected routes
            return;
        }

        try {
            Claims claims = tokenProvider.parse(token);

            if (!JwtTokenProvider.TYPE_ACCESS.equals(tokenProvider.getTokenType(claims))) {
                throw new InvalidTokenException("Invalid token");   // e.g. a refresh token used as access
            }

            UUID sessionId = tokenProvider.getSessionId(claims);
            UserSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new InvalidTokenException("Invalid token"));
            if (!session.isActive()) {
                throw new InvalidTokenException("Session revoked or expired");
            }

            User user = userRepository.findById(tokenProvider.getUserId(claims))
                    .orElseThrow(() -> new InvalidTokenException("Invalid token"));
            if (user.isAccountLocked()) {
                throw new InvalidTokenException("Account locked");
            }

            var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            // Carry the session id so endpoints (logout, session management) know which session made the call.
            authentication.setDetails(sessionId);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            chain.doFilter(request, response);
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, ex);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void writeError(HttpServletResponse response, ApiException ex) throws IOException {
        response.setStatus(ex.getStatus().value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(ex.getErrorCode(), ex.getMessage()));
    }
}
