package com.security.project.domain.signal.dto;

import com.security.project.domain.signal.entity.OneTimePreKey;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A single one-time pre-key. {@code publicKey} is Base64-encoded on the wire (Jackson maps
 * {@code byte[]} to/from Base64 automatically).
 */
public record PreKeyDto(
        @Min(0)
        int keyId,

        @NotNull
        byte[] publicKey
) {
    public static PreKeyDto from(OneTimePreKey key) {
        return new PreKeyDto(key.getKeyId(), key.getPublicKey());
    }
}
