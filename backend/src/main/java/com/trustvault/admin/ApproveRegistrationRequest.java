package com.trustvault.admin;

import com.trustvault.user.Role;

public record ApproveRegistrationRequest(
    Role role
) {}
