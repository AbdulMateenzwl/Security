package com.security.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-endpoint rate limits, bound from {@code app.rate-limit.*}.
 *
 * <p>Each {@link Limit} is a token bucket: {@code capacity} is the maximum burst, and
 * {@code refillTokens} tokens are added over {@code refillDurationSeconds} for the sustained rate.</p>
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        Limit login,
        Limit register,
        Limit messageSend,
        Limit preKeyFetch
) {
    public record Limit(int capacity, int refillTokens, int refillDurationSeconds) {
    }
}
