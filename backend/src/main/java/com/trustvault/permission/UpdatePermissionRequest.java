package com.trustvault.permission;

import jakarta.validation.constraints.NotNull;

public record UpdatePermissionRequest(
    @NotNull(message = "Permission level is required (READ or EDIT)")
    PermissionLevel permission
) {}
