package com.security.project.domain.signal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Upload of a user's public identity key and Signal registration id.
 *
 * <p>Only the public key is accepted — the server must never receive a private key. {@code publicKey}
 * is Base64-encoded on the wire.</p>
 */
public record IdentityKeyUploadRequest(
        @NotNull
        byte[] publicKey,

        @Min(1)
        int registrationId
) {
}
