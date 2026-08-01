package com.security.project.domain.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.security.project.config.properties.JwtProperties;
import com.security.project.domain.user.dto.LoginRequest;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.entity.UserSession;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.domain.user.repository.UserSessionRepository;
import com.security.project.security.jwt.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserService userService;
    @Mock UserRepository userRepository;
    @Mock UserSessionRepository sessionRepository;
    @Mock JwtTokenProvider tokenProvider;
    @Mock JwtProperties jwtProps;

    @InjectMocks AuthService service;

    @Test
    void login_revokesAllExistingSessions_beforeIssuingNewOne() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("abd");

        when(userRepository.findByUsername("abd")).thenReturn(Optional.of(user));
        when(userService.isLockedOut(user)).thenReturn(false);
        when(userService.passwordMatches("pw", user)).thenReturn(true);
        // Echo back the saved session with an id, as the DB would.
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        service.login(new LoginRequest("abd", "pw"), "1.2.3.4", "device");

        // Every prior session is revoked, and it happens before the new session is persisted.
        var order = org.mockito.Mockito.inOrder(sessionRepository);
        order.verify(sessionRepository).revokeAllForUser(eq(userId), any(Instant.class));
        order.verify(sessionRepository).save(any(UserSession.class));
        verify(sessionRepository, times(1)).save(any(UserSession.class));
    }
}
