package com.security.project.domain.signal.dto;

import java.util.UUID;

/**
 * Everything a peer needs to initiate a Signal session with a user.
 *
 * <p>The {@code oneTimePreKey} may be {@code null} if the user has exhausted their OTPKs — the
 * Signal protocol still allows a session to be established without one (with slightly weaker forward
 * secrecy for the first message), so this is not an error.</p>
 *
 * @param userId        the owner of the keys
 * @param registrationId the owner's Signal registration id
 * @param identityKey   the owner's public identity key (Base64)
 * @param signedPreKey  the owner's current signed pre-key
 * @param oneTimePreKey a freshly consumed one-time pre-key, or {@code null} if none remain
 */
public record PreKeyBundleDto(
        UUID userId,
        int registrationId,
        byte[] identityKey,
        SignedPreKeyDto signedPreKey,
        PreKeyDto oneTimePreKey
) {
}
