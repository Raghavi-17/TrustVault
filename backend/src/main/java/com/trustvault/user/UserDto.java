package com.trustvault.user;

import java.time.Instant;

public record UserDto(
    String id,
    String username,
    String fullName,
    String email,
    Role role,
    UserStatus status,
    boolean isActive,
    Instant createdAt
) {
    public static UserDto fromEntity(User user) {
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getRole(),
            user.getStatus(),
            user.isActive(),
            user.getCreatedAt()
        );
    }
}
