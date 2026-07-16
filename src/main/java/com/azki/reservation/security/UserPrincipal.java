package com.azki.reservation.security;

/** Minimal principal carried in the SecurityContext after JWT validation. */
public record UserPrincipal(Long userId, String username) {}
