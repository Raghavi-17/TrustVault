package com.trustvault.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GrantPermissionRequest(
    @NotBlank(message = "User ID is required")
    String userId,

    @NotNull(message = "Permission level is required (READ or EDIT)")
    PermissionLevel permission
) {}
