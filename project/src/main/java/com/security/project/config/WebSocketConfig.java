package com.security.project.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.security.project.config.properties.AppSecurityProperties;
import com.security.project.security.websocket.StompAuthChannelInterceptor;

/**
 * STOMP-over-WebSocket realtime configuration.
 *
 * <p>Clients connect to {@code /ws} (with SockJS fallback), authenticating by sending their access
 * token in the STOMP {@code CONNECT} frame ({@code Authorization: Bearer …}) — not in the URL, so it
 * never leaks into proxy access logs. A simple in-memory broker relays to {@code /topic/**}; client
 * sends are routed to {@code @MessageMapping} handlers under {@code /app}. Authentication and
 * per-subscription authorization are enforced by {@link StompAuthChannelInterceptor} on the inbound
 * channel.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 64 KB cap per WebSocket message — matches the REST ciphertext expectations. */
    private static final int MAX_MESSAGE_SIZE = 64 * 1024;

    private final StompAuthChannelInterceptor authChannelInterceptor;
    private final AppSecurityProperties securityProps;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor,
                           AppSecurityProperties securityProps) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.securityProps = securityProps;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = securityProps.allowedOrigins().toArray(String[]::new);
        // Native WebSocket endpoint. Authentication happens on the STOMP CONNECT frame (see the
        // channel interceptor), so no handshake interceptor is needed to capture a URL token.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins);
        // SockJS fallback for browsers without native WebSocket.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
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
