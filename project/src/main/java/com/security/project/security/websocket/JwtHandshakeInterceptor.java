package com.security.project.security.websocket;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Captures the JWT passed as {@code /ws?token=<jwt>} on the WebSocket handshake and stashes it in the
 * session attributes. The token is validated later, on the STOMP CONNECT frame, by
 * {@link StompAuthChannelInterceptor} — this interceptor only extracts it.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** Session-attribute key under which the raw token is stored for the channel interceptor. */
    public static final String TOKEN_ATTRIBUTE = "jwtToken";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractToken(request.getURI().getQuery());
        if (token != null) {
            attributes.put(TOKEN_ATTRIBUTE, token);
        }
        return true;   // always complete the handshake; CONNECT enforces authentication
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String extractToken(String query) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "token".equals(pair.substring(0, eq))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
