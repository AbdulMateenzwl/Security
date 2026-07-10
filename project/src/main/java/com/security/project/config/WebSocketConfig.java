package com.security.project.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.security.project.config.properties.AppSecurityProperties;
import com.security.project.security.websocket.JwtHandshakeInterceptor;
import com.security.project.security.websocket.StompAuthChannelInterceptor;

/**
 * STOMP-over-WebSocket realtime configuration.
 *
 * <p>Clients connect to {@code /ws} (with SockJS fallback), authenticating by passing their access
 * token as a {@code ?token=} query parameter on the handshake. A simple in-memory broker relays to
 * {@code /topic/**}; client sends are routed to {@code @MessageMapping} handlers under {@code /app}.
 * Authentication and per-subscription authorization are enforced by
 * {@link StompAuthChannelInterceptor} on the inbound channel.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 64 KB cap per WebSocket message — matches the REST ciphertext expectations. */
    private static final int MAX_MESSAGE_SIZE = 64 * 1024;

    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final StompAuthChannelInterceptor authChannelInterceptor;
    private final AppSecurityProperties securityProps;

    public WebSocketConfig(JwtHandshakeInterceptor handshakeInterceptor,
                           StompAuthChannelInterceptor authChannelInterceptor,
                           AppSecurityProperties securityProps) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.authChannelInterceptor = authChannelInterceptor;
        this.securityProps = securityProps;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = securityProps.allowedOrigins().toArray(String[]::new);
        // Native WebSocket endpoint.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .addInterceptors(handshakeInterceptor);
        // SockJS fallback for browsers without native WebSocket.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .addInterceptors(handshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_MESSAGE_SIZE);
    }
}
