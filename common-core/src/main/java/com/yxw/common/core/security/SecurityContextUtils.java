package com.yxw.common.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Helper methods for reading authenticated user information from Spring Security.
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(AuthenticatedUser::userId);
    }

    public static Long requireCurrentUserId() {
        return currentUserId().orElseThrow(() -> new IllegalStateException("Authenticated user is required."));
    }

    public static Optional<String> currentUsername() {
        return currentUser().map(AuthenticatedUser::username);
    }

    public static Optional<String> currentBearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        Object credentials = authentication.getCredentials();
        if (credentials instanceof String token && StringUtils.hasText(token)) {
            return Optional.of(token);
        }
        return Optional.empty();
    }
}
