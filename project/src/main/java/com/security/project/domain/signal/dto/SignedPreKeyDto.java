package com.security.project.domain.signal.dto;

import com.security.project.domain.signal.entity.SignedPreKey;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A signed pre-key: public key plus the identity-key signature over it. Byte fields are Base64 on
 * the wire.
 */
public record SignedPreKeyDto(
        @Min(0)
        int keyId,

        @NotNull
        byte[] publicKey,

        @NotNull
        byte[] signature
) {
    public static SignedPreKeyDto from(SignedPreKey key) {
        return new SignedPreKeyDto(key.getKeyId(), key.getPublicKey(), key.getSignature());
    }
}
