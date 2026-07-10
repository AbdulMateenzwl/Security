package com.security.project.domain.signal.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Batch upload of one-time pre-keys, optionally rotating the signed pre-key in the same call.
 *
 * @param preKeys      the one-time pre-keys to store (at least one)
 * @param signedPreKey a new signed pre-key to store, or {@code null} to keep the current one
 */
public record PreKeyUploadRequest(
        @NotEmpty
        @Valid
        List<PreKeyDto> preKeys,

        @Valid
        SignedPreKeyDto signedPreKey
) {
}
