package com.security.project.domain.signal.dto;

/**
 * Remaining unconsumed one-time pre-keys for the current user. Clients upload more when this gets
 * low so peers can always initiate a session.
 */
public record PreKeyCountResponse(long count) {
}
