package com.security.project.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fail-fast guard: refuses to start the {@code prod} profile with insecure development defaults, so a
 * misconfigured deployment can never silently run with a weak DB/Redis password, an unauthenticated
 * Redis, or an ephemeral JWT key pair.
 *
 * <p>Only active under {@code prod}; dev/test keep their convenient defaults.</p>
 */
@Component
@Profile("prod")
public class ProdSecretsValidator {

    public ProdSecretsValidator(
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${app.jwt.private-key:}") String jwtPrivateKey,
            @Value("${app.jwt.public-key:}") String jwtPublicKey) {

        List<String> problems = new ArrayList<>();
        if (!StringUtils.hasText(jwtPrivateKey) || !StringUtils.hasText(jwtPublicKey)) {
            problems.add("JWT_PRIVATE_KEY / JWT_PUBLIC_KEY must be provided in production "
                    + "(the ephemeral dev key pair is not acceptable)");
        }
        if (!StringUtils.hasText(dbPassword) || "securechat".equals(dbPassword)) {
            problems.add("DB_PASSWORD must be set to a strong, non-default value");
        }
        if (!StringUtils.hasText(redisPassword) || "securechat-redis-dev".equals(redisPassword)) {
            problems.add("REDIS_PASSWORD must be set to a strong, non-default value");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start the 'prod' profile with insecure configuration: "
                            + String.join("; ", problems));
        }
    }
}
