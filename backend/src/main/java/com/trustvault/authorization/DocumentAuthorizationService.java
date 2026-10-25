package com.trustvault.authorization;

import com.trustvault.audit.AuditOutcome;
import com.trustvault.audit.AuditService;
import com.trustvault.audit.AuditEventType;
import com.trustvault.common.AccessForbiddenException;
import com.trustvault.common.AccountDeactivatedException;
import com.trustvault.common.ResourceNotFoundException;
import com.trustvault.document.Document;
import com.trustvault.document.DocumentRepository;
import com.trustvault.permission.DocumentPermission;
import com.trustvault.permission.DocumentPermissionRepository;
import com.trustvault.permission.PermissionLevel;
import com.trustvault.user.Role;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DocumentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAuthorizationService.class);

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public DocumentAuthorizationService(
            DocumentRepository documentRepository,
            DocumentPermissionRepository permissionRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Verifies that the user exists and is active.
     */
    public User checkActiveUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccessForbiddenException("User not found"));
        if (!user.isActive()) {
            throw new AccountDeactivatedException("Account is deactivated. Access denied.");
        }
        return user;
    }

    /**
     * Resolves the effective permission for a user on a given document:
     * - "OWNER" if user is the document owner
     * - "EDIT" if user has EDIT grant
     * - "READ" if user has READ grant
     * - null if no permission
     */
    @Transactional(readOnly = true)
    public String resolveEffectivePermission(Document document, String userId) {
        if (document.getOwner().getId().equals(userId)) {
            return "OWNER";
        }
        return permissionRepository.findByDocumentIdAndUserId(document.getId(), userId)
                .filter(p -> !p.isExpired())
                .map(p -> p.getPermission().name())
                .orElse(null);
    }

    /**
     * Authorizes READ access (view metadata, download).
     * Accessible by: Document OWNER, or user with READ / EDIT permission.
     * Note: ADMIN does NOT have blanket read access to confidential documents.
     */
    @Transactional(readOnly = true)
    public Document authorizeRead(String documentId, String userId, String clientIp, String userAgent) {
        User user = checkActiveUser(userId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        Optional<DocumentPermission> grant = permissionRepository.findByDocumentIdAndUserId(documentId, userId);
        if (grant.isPresent()) {
            if (grant.get().isExpired()) {
                log.warn("Access denied on document {}: Permission grant for user {} has expired at {}",
                        documentId, user.getUsername(), grant.get().getExpiresAt());
                auditService.recordEvent(
                        user.getId(),
                        user.getUsername(),
                        AuditEventType.ACCESS_DENIED,
                        "DOCUMENT",
                        documentId,
                        AuditOutcome.DENIED,
                        clientIp,
                        userAgent,
                        null,
                        "Denied read attempt: Permission grant expired at " + grant.get().getExpiresAt()
                );
                throw new AccessForbiddenException("Your permission to access this document has expired");
            }
            return document;
        }

        // Access denied
        log.warn("Unauthorized READ attempt: user {} on document {}", user.getUsername(), documentId);
        auditService.recordEvent(
                user.getId(),
                user.getUsername(),
                AuditEventType.ACCESS_DENIED,
                "DOCUMENT",
                documentId,
                AuditOutcome.DENIED,
                clientIp,
                userAgent,
                null,
                "Denied unauthorized READ attempt"
        );
        throw new AccessForbiddenException("You do not have permission to view or download this document");
    }

    /**
     * Authorizes PREVIEW access (in-browser viewing of document content).
     * Accessible by: Document OWNER, or user with active VIEW, READ, DOWNLOAD, or EDIT permission.
     * Note: VIEW permission is explicitly PERMITTED for preview, while being restricted for download.
     */
    @Transactional(readOnly = true)
    public Document authorizePreview(String documentId, String userId, String clientIp, String userAgent) {
        User user = checkActiveUser(userId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        Optional<DocumentPermission> grant = permissionRepository.findByDocumentIdAndUserId(documentId, userId);
        if (grant.isPresent()) {
            DocumentPermission perm = grant.get();
            if (perm.isExpired()) {
                log.warn("Preview access denied on document {}: Permission grant for user {} has expired at {}",
                        documentId, user.getUsername(), perm.getExpiresAt());
                auditService.recordEvent(
                        user.getId(),
                        user.getUsername(),
                        AuditEventType.ACCESS_DENIED,
                        "DOCUMENT",
                        documentId,
                        AuditOutcome.DENIED,
                        clientIp,
                        userAgent,
                        null,
                        "Denied preview attempt: Permission grant expired at " + perm.getExpiresAt()
                );
                throw new AccessForbiddenException("Your permission to preview this document has expired");
            }
            return document;
        }

        // Access denied
        log.warn("Unauthorized PREVIEW attempt: user {} on document {}", user.getUsername(), documentId);
        auditService.recordEvent(
                user.getId(),
                user.getUsername(),
                AuditEventType.ACCESS_DENIED,
                "DOCUMENT",
                documentId,
                AuditOutcome.DENIED,
                clientIp,
                userAgent,
                null,
                "Denied unauthorized PREVIEW attempt"
        );
        throw new AccessForbiddenException("You do not have permission to preview this document");
    }

    /**
     * Authorizes DOWNLOAD access (downloading actual private document bytes).
     * Accessible by: Document OWNER, or user with DOWNLOAD, READ, or EDIT permission.
     * Users with VIEW-only permission are explicitly rejected.
     */
    @Transactional(readOnly = true)
    public Document authorizeDownload(String documentId, String userId, String clientIp, String userAgent) {
        User user = checkActiveUser(userId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        Optional<DocumentPermission> grant = permissionRepository.findByDocumentIdAndUserId(documentId, userId);
        if (grant.isPresent()) {
            DocumentPermission perm = grant.get();
            if (perm.isExpired()) {
                log.warn("Download access denied on document {}: Permission grant for user {} has expired at {}",
                        documentId, user.getUsername(), perm.getExpiresAt());
                auditService.recordEvent(
                        user.getId(),
                        user.getUsername(),
                        AuditEventType.ACCESS_DENIED,
                        "DOCUMENT",
                        documentId,
                        AuditOutcome.DENIED,
                        clientIp,
                        userAgent,
                        null,
                        "Denied download attempt: Permission grant expired at " + perm.getExpiresAt()
                );
                throw new AccessForbiddenException("Your permission to download this document has expired");
            }

            PermissionLevel level = perm.getPermission();
            if (level == PermissionLevel.VIEW) {
                log.warn("Download access denied on document {}: User {} has VIEW permission only", documentId, user.getUsername());
                auditService.recordEvent(
                        user.getId(),
                        user.getUsername(),
                        AuditEventType.ACCESS_DENIED,
                        "DOCUMENT",
                        documentId,
                        AuditOutcome.DENIED,
                        clientIp,
                        userAgent,
                        null,
                        "Denied download attempt: User has VIEW-only permission (download restricted)"
                );
                throw new AccessForbiddenException("You have VIEW permission only. Downloading this document is not permitted.");
            }

            // Allowed for DOWNLOAD, READ (legacy), or EDIT
            return document;
        }

        // Access denied
        log.warn("Unauthorized DOWNLOAD attempt: user {} on document {}", user.getUsername(), documentId);
        auditService.recordEvent(
                user.getId(),
                user.getUsername(),
                AuditEventType.ACCESS_DENIED,
                "DOCUMENT",
                documentId,
                AuditOutcome.DENIED,
                clientIp,
                userAgent,
                null,
                "Denied unauthorized DOWNLOAD attempt"
        );
        throw new AccessForbiddenException("You do not have permission to download this document");
    }

    /**
     * Authorizes EDIT access (update metadata, replace content).
     * Accessible by: Document OWNER, or user with EDIT permission.
     */
    @Transactional(readOnly = true)
    public Document authorizeEdit(String documentId, String userId, String clientIp, String userAgent) {
        User user = checkActiveUser(userId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        Optional<DocumentPermission> grant = permissionRepository.findByDocumentIdAndUserId(documentId, userId);
        if (grant.isPresent()) {
            if (grant.get().isExpired()) {
                log.warn("Edit access denied on document {}: Permission grant for user {} has expired at {}",
                        documentId, user.getUsername(), grant.get().getExpiresAt());
                auditService.recordEvent(
                        user.getId(),
                        user.getUsername(),
                        AuditEventType.ACCESS_DENIED,
                        "DOCUMENT",
                        documentId,
                        AuditOutcome.DENIED,
                        clientIp,
                        userAgent,
                        null,
                        "Denied edit attempt: Permission grant expired at " + grant.get().getExpiresAt()
                );
                throw new AccessForbiddenException("Your permission to edit this document has expired");
            }
            if (grant.get().getPermission() == PermissionLevel.EDIT) {
                return document;
            }
        }

        log.warn("Unauthorized EDIT attempt: user {} on document {}", user.getUsername(), documentId);
        auditService.recordEvent(
                user.getId(),
                user.getUsername(),
                AuditEventType.ACCESS_DENIED,
                "DOCUMENT",
                documentId,
                AuditOutcome.DENIED,
                clientIp,
                userAgent,
                null,
                "Denied unauthorized EDIT attempt"
        );
        throw new AccessForbiddenException("You do not have permission to edit this document");
    }

    /**
     * Authorizes OWNER access (manage permissions, delete document).
     * Accessible ONLY by the document OWNER.
     */
    @Transactional(readOnly = true)
    public Document authorizeOwner(String documentId, String userId, String clientIp, String userAgent, String actionName) {
        User user = checkActiveUser(userId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));

        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        log.warn("Unauthorized OWNER action ({}) attempt: user {} on document {}", actionName, user.getUsername(), documentId);
        auditService.recordEvent(
                user.getId(),
                user.getUsername(),
                AuditEventType.ACCESS_DENIED,
                "DOCUMENT",
                documentId,
                AuditOutcome.DENIED,
                clientIp,
                userAgent,
                null,
                "Denied unauthorized OWNER action: " + actionName
        );
        throw new AccessForbiddenException("Only the document owner can perform " + actionName);
    }
}
