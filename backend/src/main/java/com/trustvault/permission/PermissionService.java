package com.trustvault.permission;

import com.trustvault.audit.AuditOutcome;
import com.trustvault.audit.AuditService;
import com.trustvault.audit.AuditEventType;
import com.trustvault.authorization.DocumentAuthorizationService;
import com.trustvault.common.BadRequestException;
import com.trustvault.common.ResourceNotFoundException;
import com.trustvault.document.Document;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final DocumentPermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final DocumentAuthorizationService authorizationService;
    private final AuditService auditService;

    public PermissionService(
            DocumentPermissionRepository permissionRepository,
            UserRepository userRepository,
            DocumentAuthorizationService authorizationService,
            AuditService auditService) {
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> getDocumentPermissions(String documentId, String actorId, String clientIp, String userAgent) {
        authorizationService.authorizeOwner(documentId, actorId, clientIp, userAgent, "viewing document permissions");
        return permissionRepository.findByDocumentId(documentId).stream()
                .map(PermissionDto::fromEntity)
                .toList();
    }

    @Transactional
    public PermissionDto grantPermission(String documentId, GrantPermissionRequest request, String actorId, String clientIp, String userAgent) {
        Document document = authorizationService.authorizeOwner(documentId, actorId, clientIp, userAgent, "granting permissions");
        User granter = authorizationService.checkActiveUser(actorId);

        User targetUser = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found: " + request.userId()));

        if (targetUser.getId().equals(granter.getId())) {
            throw new BadRequestException("Document owner already possesses full privileges");
        }

        if (permissionRepository.existsByDocumentIdAndUserId(documentId, targetUser.getId())) {
            throw new BadRequestException("Permission already exists for user: " + targetUser.getUsername() + ". Use update permission to modify.");
        }

        DocumentPermission permission = new DocumentPermission(document, targetUser, request.permission(), granter, request.expiresAt());
        DocumentPermission saved = permissionRepository.save(permission);

        String expiresMsg = request.expiresAt() != null ? " (expires: " + request.expiresAt() + ")" : "";
        auditService.recordEvent(
                granter.getId(),
                granter.getUsername(),
                AuditEventType.PERMISSION_GRANT,
                "PERMISSION",
                saved.getId(),
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Granted " + request.permission() + " access on '" + document.getTitle() + "' to user " + targetUser.getUsername() + expiresMsg
        );

        return PermissionDto.fromEntity(saved);
    }

    @Transactional
    public PermissionDto updatePermission(String documentId, String targetUserId, UpdatePermissionRequest request, String actorId, String clientIp, String userAgent) {
        Document document = authorizationService.authorizeOwner(documentId, actorId, clientIp, userAgent, "modifying permissions");
        User granter = authorizationService.checkActiveUser(actorId);

        DocumentPermission permission = permissionRepository.findByDocumentIdAndUserId(documentId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission record not found for user: " + targetUserId));

        PermissionLevel oldLevel = permission.getPermission();
        permission.setPermission(request.permission());
        if (request.expiresAt() != null) {
            permission.setExpiresAt(request.expiresAt());
        }
        permission.setUpdatedAt(Instant.now());

        DocumentPermission updated = permissionRepository.save(permission);

        auditService.recordEvent(
                granter.getId(),
                granter.getUsername(),
                AuditEventType.PERMISSION_MODIFY,
                "PERMISSION",
                updated.getId(),
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Modified permission for user " + permission.getUser().getUsername() + " on '" + document.getTitle() + "' from " + oldLevel + " to " + request.permission()
        );

        return PermissionDto.fromEntity(updated);
    }

    @Transactional
    public void revokePermission(String documentId, String targetUserId, String actorId, String clientIp, String userAgent) {
        Document document = authorizationService.authorizeOwner(documentId, actorId, clientIp, userAgent, "revoking permissions");
        User granter = authorizationService.checkActiveUser(actorId);

        DocumentPermission permission = permissionRepository.findByDocumentIdAndUserId(documentId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission record not found for user: " + targetUserId));

        String revokedUsername = permission.getUser().getUsername();
        PermissionLevel revokedLevel = permission.getPermission();

        permissionRepository.delete(permission);

        // Immediate revocation takes effect immediately in the database
        auditService.recordEvent(
                granter.getId(),
                granter.getUsername(),
                AuditEventType.PERMISSION_REVOKE,
                "PERMISSION",
                permission.getId(),
                AuditOutcome.SUCCESS,
                clientIp,
                userAgent,
                null,
                "Revoked " + revokedLevel + " access on '" + document.getTitle() + "' from user " + revokedUsername
        );
    }
}
