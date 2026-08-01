package com.security.project.security.websocket;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.security.project.domain.chat.service.ChatAccessGuard;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.entity.UserSession;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.domain.user.repository.UserSessionRepository;
import com.security.project.exception.ApiException;
import com.security.project.security.jwt.JwtTokenProvider;

import io.jsonwebtoken.Claims;

/**
 * Enforces WebSocket security on the inbound STOMP channel.
 *
 * <ul>
 *   <li><b>CONNECT</b> — validates the token from the frame's Authorization header (same rules as the HTTP filter:
 *       access token, active session, existing unlocked user) and binds the authenticated user id to
 *       the STOMP session. A bad/missing token aborts the connection with a STOMP ERROR frame.</li>
 *   <li><b>SUBSCRIBE</b> — authorizes the destination: {@code /topic/chat/{chatId}} (and its
 *       {@code /typing} sub-topic) and {@code /topic/tasks/{chatId}} require chat membership;
 *       {@code /topic/user/{userId}} is only allowed for one's own id.</li>
 * </ul>
 *
 * <p>Note: the token is checked once at CONNECT (a long-lived connection is not re-validated per
 * frame), so session revocation stops new connections rather than tearing down existing ones.</p>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider tokenProvider;
    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ChatAccessGuard chatAccessGuard;

    public StompAuthChannelInterceptor(JwtTokenProvider tokenProvider,
                                       UserSessionRepository sessionRepository,
                                       UserRepository userRepository,
                                       ChatAccessGuard chatAccessGuard) {
        this.tokenProvider = tokenProvider;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.chatAccessGuard = chatAccessGuard;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        // The access token is sent in the CONNECT frame's Authorization header (never in the URL, so
        // it can't leak into proxy access logs or browser history).
        String token = bearerToken(accessor.getFirstNativeHeader("Authorization"));
        if (token == null) {
            throw new MessagingException("Missing authentication token");
        }
        try {
            Claims claims = tokenProvider.parse(token);
            if (!JwtTokenProvider.TYPE_ACCESS.equals(tokenProvider.getTokenType(claims))) {
                throw new MessagingException("Invalid token");
            }
            UserSession session = sessionRepository.findById(tokenProvider.getSessionId(claims))
                    .orElseThrow(() -> new MessagingException("Invalid token"));
            if (!session.isActive()) {
                throw new MessagingException("Session revoked or expired");
            }
            User user = userRepository.findById(tokenProvider.getUserId(claims))
                    .orElseThrow(() -> new MessagingException("Invalid token"));
            if (user.isAccountLocked()) {
                throw new MessagingException("Account locked");
            }
            // Principal name = user id, which @MessageMapping handlers and subscription checks read.
            return new UsernamePasswordAuthenticationToken(
                    user.getId().toString(), null, user.getAuthorities());
        } catch (ApiException ex) {
            throw new MessagingException("Authentication failed");
        }
    }

    /** Extract the raw JWT from a {@code Bearer <token>} header value, or null. */
    private String bearerToken(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Principal principal = accessor.getUser();
        if (destination == null || principal == null) {
            throw new MessagingException("Unauthorized subscription");
        }
        UUID userId = UUID.fromString(principal.getName());

        if (destination.startsWith("/topic/chat/")) {
            requireMembership(parseId(destination, "/topic/chat/"), userId);
        } else if (destination.startsWith("/topic/tasks/")) {
            requireMembership(parseId(destination, "/topic/tasks/"), userId);
        } else if (destination.startsWith("/topic/user/")) {
            if (!parseId(destination, "/topic/user/").equals(userId)) {
                throw new MessagingException("Cannot subscribe to another user's channel");
            }
        }
        // Any other destination requires only an authenticated session (already established).
    }

    private void requireMembership(UUID chatId, UUID userId) {
        if (!chatAccessGuard.isMember(chatId, userId)) {
            throw new MessagingException("Not a member of this chat");
        }
    }

    /** Parse the first path segment after {@code prefix} as a UUID (ignores any trailing sub-path). */
    private UUID parseId(String destination, String prefix) {
        String rest = destination.substring(prefix.length());
        int slash = rest.indexOf('/');
        String id = slash >= 0 ? rest.substring(0, slash) : rest;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new MessagingException("Invalid subscription destination");
        }
    }
}
