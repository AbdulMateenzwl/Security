package com.security.project.domain.user.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.security.project.domain.user.dto.FingerprintResponse;
import com.security.project.domain.user.service.UserService;

/**
 * User lookup endpoints.
 *
 * <p>Requires authentication like the rest of the API. The fingerprint it returns is public key
 * material (a safety number) by design — no per-user authorization is applied, so any authenticated
 * user may look up any other user's fingerprint to perform an out-of-band MITM check.</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** The user's identity-key fingerprint (safety number). 404 if the user does not exist. */
    @GetMapping("/{userId}/fingerprint")
    public FingerprintResponse fingerprint(@PathVariable UUID userId) {
        return userService.getFingerprint(userId);
    }
}
