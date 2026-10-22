package com.trustvault.auth;

import com.trustvault.common.AccessForbiddenException;
import com.trustvault.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<CustomUserDetails> getAuthenticatedUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return Optional.of(userDetails);
        }
        return Optional.empty();
    }

    public static CustomUserDetails getRequiredAuthenticatedUser() {
        return getAuthenticatedUserDetails()
                .orElseThrow(() -> new AccessForbiddenException("User is not authenticated"));
    }

    public static String getCurrentUserId() {
        return getAuthenticatedUserDetails()
                .map(CustomUserDetails::getId)
                .orElse(null);
    }

    public static String getCurrentUsername() {
        return getAuthenticatedUserDetails()
                .map(CustomUserDetails::getUsername)
                .orElse("ANONYMOUS");
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
