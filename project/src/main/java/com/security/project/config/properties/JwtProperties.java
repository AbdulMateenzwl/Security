package com.security.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from the {@code app.jwt.*} namespace.
 *
 * <p>Tokens are signed with RS256 (asymmetric RSA). The private key signs tokens; the public key
 * verifies them. Keys are supplied as PEM strings via environment variables so that private key
 * material never lives in source control.</p>
 *
 * @param privateKey        RSA private key in PEM form (may be blank in dev — see JwtKeyConfig)
 * @param publicKey         RSA public key in PEM form (may be blank in dev — see JwtKeyConfig)
 * @param issuer            the {@code iss} claim value; verified on every token
 * @param accessExpirationMs  access-token lifetime in milliseconds (short-lived)
 * @param refreshExpirationMs refresh-token lifetime in milliseconds (longer-lived)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        String issuer,
        long accessExpirationMs,
        long refreshExpirationMs
) {
}
