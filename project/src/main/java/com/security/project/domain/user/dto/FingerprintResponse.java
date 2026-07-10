package com.security.project.domain.user.dto;

import java.util.UUID;

/**
 * A user's identity-key fingerprint (the "safety number"), for out-of-band MITM verification.
 *
 * <p>The fingerprint is derived from the public identity key only — no secret material. It is
 * {@code null} until the user has uploaded an identity key. Clients compare it against their local
 * Signal session fingerprint to detect a man-in-the-middle.</p>
 *
 * @param userId      the user
 * @param username    the user's username
 * @param fingerprint hex fingerprint of the identity key, or null if none has been published yet
 */
public record FingerprintResponse(
        UUID userId,
        String username,
        String fingerprint
) {
}
