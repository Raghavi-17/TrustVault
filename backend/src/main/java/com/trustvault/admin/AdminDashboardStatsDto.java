package com.trustvault.admin;

import com.trustvault.audit.AuditEventDto;
import com.trustvault.audit.AuditVerificationResultDto;

import java.util.List;

public record AdminDashboardStatsDto(
    long totalUsers,
    long activeUsers,
    long deactivatedUsers,
    long pendingRegistrations,
    long totalDocuments,
    long totalUploads,
    long totalDownloads,
    long totalAccessDenied,
    long totalFailedLogins,
    long totalPermissionChanges,
    AuditVerificationResultDto auditIntegrityStatus,
    List<AuditEventDto> recentSecurityEvents
) {}
