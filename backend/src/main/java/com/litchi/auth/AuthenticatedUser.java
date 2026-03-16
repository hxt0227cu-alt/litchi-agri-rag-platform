package com.litchi.auth;

public record AuthenticatedUser(
        String id,
        String username,
        String role,
        String createdAt
) {
}
