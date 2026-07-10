package com.security.project.security.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.security.project.config.properties.RateLimitProperties.Limit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;

/**
 * Shared entry point for consuming rate-limit tokens against the Redis-backed Bucket4j buckets.
 *
 * <p>Both the HTTP {@link RateLimitFilter} and the WebSocket message handler go through here, using
 * the same {@code rate:{name}:{identity}} key scheme — so, for example, a user's message-send limit
 * is enforced consistently whether they post over REST or over the socket.</p>
 */
@Component
public class RateLimiterService {

    private final LettuceBasedProxyManager<byte[]> proxyManager;

    public RateLimiterService(LettuceBasedProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    /** Try to consume one token from the {@code rate:{bucketName}:{identity}} bucket. */
    public ConsumptionProbe tryConsume(String bucketName, String identity, Limit limit) {
        String key = "rate:" + bucketName + ":" + identity;
        Bucket bucket = proxyManager.builder()
                .build(key.getBytes(StandardCharsets.UTF_8), configFor(limit));
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private Supplier<BucketConfiguration> configFor(Limit limit) {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillGreedy(limit.refillTokens(), Duration.ofSeconds(limit.refillDurationSeconds()))
                        .build())
                .build();
    }
}
