package com.security.project.security.jwt;

import java.security.KeyPair;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.security.project.config.properties.JwtProperties;
import com.security.project.domain.user.entity.User;
import com.security.project.exception.InvalidTokenException;
import com.security.project.exception.TokenExpiredException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/**
 * Creates and verifies RS256-signed JWTs.
 *
 * <p>Every token carries:</p>
 * <ul>
 *   <li>{@code sub} — the user id</li>
 *   <li>{@code sid} — the {@link com.security.project.domain.user.entity.UserSession} id, so a token
 *       can be revoked server-side by revoking its session</li>
 *   <li>{@code typ} — {@code access} or {@code refresh}, so a refresh token can never be used as an
 *       access token (or vice versa)</li>
 *   <li>{@code jti} — a unique id per token</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_SESSION_ID = "sid";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final KeyPair keyPair;
    private final JwtProperties props;

    public JwtTokenProvider(KeyPair jwtKeyPair, JwtProperties props) {
        this.keyPair = jwtKeyPair;
        this.props = props;
    }

    /** Mint a short-lived access token bound to the given session. */
    public String generateAccessToken(User user, UUID sessionId) {
        return buildToken(user, sessionId, TYPE_ACCESS, props.accessExpirationMs(), UUID.randomUUID().toString());
    }

    /**
     * Mint a longer-lived refresh token bound to the given session and the given {@code jti}. The jti
     * is stored on the session so it can be rotated and old refresh tokens detected as reuse.
     */
    public String generateRefreshToken(User user, UUID sessionId, String jti) {
        return buildToken(user, sessionId, TYPE_REFRESH, props.refreshExpirationMs(), jti);
    }

    private String buildToken(User user, UUID sessionId, String type, long ttlMs, String jti) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);
        return Jwts.builder()
                .id(jti)
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Verify signature, issuer and expiry, returning the claims.
     *
     * @throws TokenExpiredException if the token is expired
     * @throws InvalidTokenException if the token is malformed, unsigned, or otherwise invalid
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(keyPair.getPublic())
                    .requireIssuer(props.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("Token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UUID getSessionId(Claims claims) {
        return UUID.fromString(claims.get(CLAIM_SESSION_ID, String.class));
    }

    public String getTokenType(Claims claims) {
        return claims.get(CLAIM_TOKEN_TYPE, String.class);
    }

    /** The token's unique id (jti) — used to detect refresh-token reuse. */
    public String getJti(Claims claims) {
        return claims.getId();
    }

    public long getAccessExpirationMs() {
        return props.accessExpirationMs();
    }
}
