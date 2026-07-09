package com.security.project.config;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.security.project.config.properties.JwtProperties;

/**
 * Provides the RSA {@link KeyPair} used to sign and verify JWTs (RS256).
 *
 * <p>Keys are read from {@code app.jwt.private-key} / {@code app.jwt.public-key} (PEM). If they are
 * absent, behaviour depends on the profile:</p>
 * <ul>
 *   <li><b>dev</b> — an ephemeral 2048-bit key pair is generated at startup so the app runs without
 *       external key setup. Tokens do not survive a restart. Never rely on this outside dev.</li>
 *   <li><b>any other profile</b> — the application fails fast, refusing to start without real keys.</li>
 * </ul>
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    public KeyPair jwtKeyPair(JwtProperties props, Environment env) throws Exception {
        boolean hasPrivate = StringUtils.hasText(props.privateKey());
        boolean hasPublic = StringUtils.hasText(props.publicKey());

        if (hasPrivate && hasPublic) {
            PrivateKey privateKey = parsePrivateKey(props.privateKey());
            PublicKey publicKey = parsePublicKey(props.publicKey());
            log.info("Loaded RSA JWT key pair from configuration");
            return new KeyPair(publicKey, privateKey);
        }

        boolean devProfile = env.matchesProfiles("dev") || env.getActiveProfiles().length == 0;
        if (!devProfile) {
            throw new IllegalStateException(
                    "JWT_PRIVATE_KEY and JWT_PUBLIC_KEY must be set outside the dev profile");
        }

        log.warn("No RSA JWT keys configured — generating an EPHEMERAL key pair (dev only). "
                + "Tokens will be invalidated on restart.");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private PublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    /** Strip PEM armor and whitespace, leaving raw Base64 for the DER body. */
    private String stripPem(String pem) {
        return pem
                .replaceAll("-----BEGIN (RSA )?(PRIVATE|PUBLIC) KEY-----", "")
                .replaceAll("-----END (RSA )?(PRIVATE|PUBLIC) KEY-----", "")
                .replaceAll("\\s", "");
    }
}
