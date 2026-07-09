package com.security.project.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application security configuration bound from the {@code app.security.*} namespace.
 *
 * @param maxFailedLoginAttempts          failed logins before an account is locked
 * @param accountLockDurationMinutes      how long a locked account stays locked (Redis TTL)
 * @param allowedOrigins                  CORS allow-list; wildcards are intentionally not supported
 * @param disappearingMessageCleanupCron  cron for purging expired messages (used in a later feature)
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        int maxFailedLoginAttempts,
        int accountLockDurationMinutes,
        List<String> allowedOrigins,
        String disappearingMessageCleanupCron
) {
}
