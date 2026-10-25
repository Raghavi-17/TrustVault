package com.trustvault.permission;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdatePermissionRequest(
    @NotNull(message = "Permission level is required")
    PermissionLevel permission,

    Instant expiresAt
) {
    public UpdatePermissionRequest(PermissionLevel permission) {
        this(permission, null);
    }
}
