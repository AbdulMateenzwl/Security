package com.security.project.domain.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Set (or clear) a chat's disappearing-message timer.
 *
 * @param ttlSeconds seconds before messages self-delete; {@code null} turns disappearing off.
 *                   Capped at one week.
 */
public record DisappearingTtlRequest(
        @Min(1)
        @Max(604_800)
        Integer ttlSeconds
) {
}
