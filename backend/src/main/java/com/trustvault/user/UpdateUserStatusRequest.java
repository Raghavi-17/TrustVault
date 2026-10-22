package com.trustvault.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull(message = "Active status must be specified")
    Boolean isActive
) {}
