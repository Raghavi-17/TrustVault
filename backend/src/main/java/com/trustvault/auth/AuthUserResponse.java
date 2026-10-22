package com.trustvault.auth;

import com.trustvault.user.Role;
import com.trustvault.user.User;

public record AuthUserResponse(
    String id,
    String username,
    String fullName,
    String email,
    Role role,
    boolean isActive
) {
    public static AuthUserResponse fromUser(User user) {
        return new AuthUserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getRole(),
            user.isActive()
        );
    }

    public static AuthUserResponse fromUserDetails(CustomUserDetails user) {
        return new AuthUserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getRole(),
            user.isEnabled()
        );
    }
}
