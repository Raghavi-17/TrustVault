package com.trustvault.admin;

import com.trustvault.audit.AuditEvent;
import com.trustvault.audit.AuditEventDto;
import com.trustvault.audit.AuditEventRepository;
import com.trustvault.audit.AuditEventType;
import com.trustvault.audit.AuditOutcome;
import com.trustvault.audit.AuditService;
import com.trustvault.audit.AuditVerificationResultDto;
import com.trustvault.document.DocumentRepository;
import com.trustvault.permission.DocumentPermissionRepository;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;

    public AdminService(
            UserRepository userRepository,
            DocumentRepository documentRepository,
            DocumentPermissionRepository permissionRepository,
            AuditEventRepository auditEventRepository,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardStatsDto getDashboardStats() {
        List<User> users = userRepository.findAll();
        long totalUsers = users.size();
        long activeUsers = users.stream().filter(User::isActive).count();
        long deactivatedUsers = users.stream().filter(u -> u.getStatus() == com.trustvault.user.UserStatus.DEACTIVATED || (!u.isActive() && u.getStatus() != com.trustvault.user.UserStatus.PENDING)).count();
        long pendingRegistrations = users.stream().filter(u -> u.getStatus() == com.trustvault.user.UserStatus.PENDING).count();

        long totalDocuments = documentRepository.count();
        long totalUploads = auditEventRepository.countByAction(AuditEventType.DOCUMENT_UPLOAD);
        long totalDownloads = auditEventRepository.countByAction(AuditEventType.DOCUMENT_DOWNLOAD);
        long totalAccessDenied = auditEventRepository.countByOutcome(AuditOutcome.DENIED);
        long totalFailedLogins = auditEventRepository.countByAction(AuditEventType.LOGIN_FAILURE);

        long totalPermissionGrants = auditEventRepository.countByAction(AuditEventType.PERMISSION_GRANT);
        long totalPermissionModifies = auditEventRepository.countByAction(AuditEventType.PERMISSION_MODIFY);
        long totalPermissionRevokes = auditEventRepository.countByAction(AuditEventType.PERMISSION_REVOKE);
        long totalPermissionChanges = totalPermissionGrants + totalPermissionModifies + totalPermissionRevokes;

        AuditVerificationResultDto auditIntegrityStatus = auditService.verifyIntegrity();
        List<AuditEventDto> recentSecurityEvents = auditService.getRecentSecurityActivity().stream()
                .map(AuditEventDto::fromEntity)
                .toList();

        return new AdminDashboardStatsDto(
                totalUsers,
                activeUsers,
                deactivatedUsers,
                pendingRegistrations,
                totalDocuments,
                totalUploads,
                totalDownloads,
                totalAccessDenied,
                totalFailedLogins,
                totalPermissionChanges,
                auditIntegrityStatus,
                recentSecurityEvents
        );
    }
}
