package com.security.project.domain.signal.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.security.project.domain.signal.dto.IdentityKeyUploadRequest;
import com.security.project.domain.signal.dto.PreKeyBundleDto;
import com.security.project.domain.signal.dto.PreKeyCountResponse;
import com.security.project.domain.signal.dto.PreKeyUploadRequest;
import com.security.project.domain.signal.dto.SignedPreKeyDto;
import com.security.project.domain.signal.service.SignalKeyService;
import com.security.project.domain.user.entity.User;

import jakarta.validation.Valid;

/**
 * Signal key distribution endpoints. All require authentication.
 *
 * <p>A user manages their own keys; any authenticated user may fetch another user's pre-key bundle,
 * which is required to initiate a session with them.</p>
 */
@RestController
@RequestMapping("/api/signal")
public class SignalKeyController {

    private final SignalKeyService signalKeyService;

    public SignalKeyController(SignalKeyService signalKeyService) {
        this.signalKeyService = signalKeyService;
    }

    /** Upload (or replace) the current user's identity key and registration id. */
    @PostMapping("/identity-key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadIdentityKey(@AuthenticationPrincipal User user,
                                  @Valid @RequestBody IdentityKeyUploadRequest request) {
        signalKeyService.uploadIdentityKey(user.getId(), request);
    }

    /** Batch-upload one-time pre-keys (optionally rotating the signed pre-key too). */
    @PostMapping("/pre-keys")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadPreKeys(@AuthenticationPrincipal User user,
                              @Valid @RequestBody PreKeyUploadRequest request) {
        signalKeyService.uploadPreKeys(user.getId(), request);
    }

    /** Rotate the current user's signed pre-key. */
    @PutMapping("/signed-pre-key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rotateSignedPreKey(@AuthenticationPrincipal User user,
                                   @Valid @RequestBody SignedPreKeyDto request) {
        signalKeyService.rotateSignedPreKey(user.getId(), request);
    }

    /** Fetch a peer's pre-key bundle to initiate a Signal session (consumes one of their OTPKs). */
    @GetMapping("/pre-key-bundle/{userId}")
    public PreKeyBundleDto getPreKeyBundle(@PathVariable UUID userId) {
        return signalKeyService.getPreKeyBundle(userId);
    }

    /** How many one-time pre-keys the current user has left. */
    @GetMapping("/pre-key-count")
    public PreKeyCountResponse getPreKeyCount(@AuthenticationPrincipal User user) {
        return new PreKeyCountResponse(signalKeyService.getRemainingPreKeyCount(user.getId()));
    }
}
