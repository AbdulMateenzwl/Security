package com.security.project.domain.user.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.security.project.domain.user.dto.AuthResponse;
import com.security.project.domain.user.dto.LoginRequest;
import com.security.project.domain.user.dto.RefreshRequest;
import com.security.project.domain.user.dto.RegisterRequest;
import com.security.project.domain.user.dto.SessionDto;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.service.AuthService;
import com.security.project.security.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Authentication and session-management endpoints.
 *
 * <p>{@code register}, {@code login} and {@code refresh} are public; everything else requires a
 * valid access token.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return authService.register(request, clientIp(http), userAgent(http));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, clientIp(http), userAgent(http));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        UUID sessionId = SecurityUtils.currentSessionId();
        if (sessionId != null) {
            authService.logout(sessionId);
        }
    }

    @GetMapping("/sessions")
    public List<SessionDto> listSessions(@AuthenticationPrincipal User user) {
        return authService.listSessions(user.getId(), SecurityUtils.currentSessionId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal User user, @PathVariable UUID sessionId) {
        authService.revokeSession(user.getId(), sessionId);
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@AuthenticationPrincipal User user) {
        authService.revokeAllSessions(user.getId());
    }

    // --- request metadata helpers -----------------------------------------

    /** Best-effort client IP: honour X-Forwarded-For (first hop) behind a proxy, else the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
