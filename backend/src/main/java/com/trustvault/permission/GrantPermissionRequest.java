package com.trustvault.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record GrantPermissionRequest(
    @NotBlank(message = "User ID is required")
    String userId,

    @NotNull(message = "Permission level is required")
    PermissionLevel permission,

    Instant expiresAt
) {
    public GrantPermissionRequest(String userId, PermissionLevel permission) {
        this(userId, permission, null);
    }
}
