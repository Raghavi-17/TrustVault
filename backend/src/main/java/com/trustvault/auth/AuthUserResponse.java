package com.trustvault.auth;

import com.trustvault.user.Role;
import com.trustvault.user.User;
import com.trustvault.user.UserStatus;

public record AuthUserResponse(
    String id,
    String username,
    String fullName,
    String email,
    Role role,
    UserStatus status,
    boolean isActive
) {
    public static AuthUserResponse fromUser(User user) {
        return new AuthUserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getRole(),
            user.getStatus(),
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
            user.getStatus(),
            user.isEnabled()
        );
    }
}
