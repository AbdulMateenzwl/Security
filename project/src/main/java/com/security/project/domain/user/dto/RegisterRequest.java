package com.security.project.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. All fields are validated before any processing — never trust client input.
 *
 * @param username       3–50 chars, letters/digits/._- only
 * @param email          valid email address
 * @param password       8–128 chars (strength further enforced by the client; length floor here)
 * @param githubUsername optional GitHub handle for collaboration features
 */
public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "username may contain only letters, digits, dot, underscore, hyphen")
        String username,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @Size(max = 100)
        String githubUsername
) {
}
