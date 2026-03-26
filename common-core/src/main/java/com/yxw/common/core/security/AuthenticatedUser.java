package com.yxw.common.core.security;

/**
 * Lightweight authenticated user information extracted from JWT.
 */
public record AuthenticatedUser(Long userId, String username) {
}
