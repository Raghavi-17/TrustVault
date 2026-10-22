package com.trustvault.permission;

import java.time.Instant;

public record PermissionDto(
    String id,
    String documentId,
    String userId,
    String username,
    String userFullName,
    String userEmail,
    PermissionLevel permission,
    String grantedByUsername,
    Instant createdAt,
    Instant updatedAt
) {
    public static PermissionDto fromEntity(DocumentPermission perm) {
        return new PermissionDto(
            perm.getId(),
            perm.getDocument().getId(),
            perm.getUser().getId(),
            perm.getUser().getUsername(),
            perm.getUser().getFullName(),
            perm.getUser().getEmail(),
            perm.getPermission(),
            perm.getGrantedBy().getUsername(),
            perm.getCreatedAt(),
            perm.getUpdatedAt()
        );
    }
}
