package com.security.project.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.security.project.config.properties.AppSecurityProperties;
import com.security.project.config.properties.RateLimitProperties;
import com.security.project.security.jwt.JwtAuthEntryPoint;
import com.security.project.security.jwt.JwtAuthenticationFilter;
import com.security.project.security.ratelimit.RateLimitFilter;

import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Core HTTP security configuration.
 *
 * <p>The API is stateless (JWT bearer tokens): no server session, no CSRF token, no form login. The
 * JWT filter runs before the username/password filter and populates the security context. Every
 * route requires authentication except the explicitly public ones below.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final AppSecurityProperties securityProps;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthEntryPoint jwtAuthEntryPoint,
                          AppSecurityProperties securityProps) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
        this.securityProps = securityProps;
    }

    /** BCrypt with cost factor 12 — strong work factor for password hashing. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           LettuceBasedProxyManager<byte[]> rateLimitProxyManager,
                                           RateLimitProperties rateLimitProperties,
                                           ObjectMapper objectMapper) throws Exception {
        // Constructed here (not a bean) so it lives only in the security chain — after the JWT filter,
        // so the authenticated user is available for per-user limits — and is never auto-registered
        // by Boot as a stand-alone servlet filter.
        RateLimitFilter rateLimitFilter =
                new RateLimitFilter(rateLimitProxyManager, rateLimitProperties, objectMapper);
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Stateless JWT auth — no session cookie, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()   // WebSocket handshake is authenticated separately
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /** Strict CORS: an explicit origin allow-list only — never a wildcard. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(securityProps.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Retry-After", "X-RateLimit-Limit", "X-RateLimit-Remaining"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
