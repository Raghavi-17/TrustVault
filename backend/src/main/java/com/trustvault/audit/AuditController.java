package com.trustvault.audit;

import com.trustvault.auth.SecurityUtils;
import com.trustvault.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit & Tamper Evidence", description = "Cryptographic audit trail inspection and verification")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Query audit trail events with filtering by user, action, document, date, and outcome (Admin only)")
    public ResponseEntity<ApiResponse<Page<AuditEventDto>>> getAuditEvents(
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditEventType action,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String document,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditEvent> events = auditService.getFilteredEvents(outcome, action, user, document, startDate, endDate, pageRequest);

        return ResponseEntity.ok(ApiResponse.ok(events.map(AuditEventDto::fromEntity)));
    }

    @GetMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify cryptographic SHA-256 hash chain integrity of the entire audit trail (Read-only)")
    public ResponseEntity<ApiResponse<AuditVerificationResultDto>> getChainIntegrity() {
        AuditVerificationResultDto result = auditService.verifyIntegrity();
        return ResponseEntity.ok(ApiResponse.ok("Audit integrity verification completed", result));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify cryptographic SHA-256 hash chain integrity of the entire audit trail (Admin only)")
    public ResponseEntity<ApiResponse<AuditVerificationResultDto>> verifyChainIntegrity(HttpServletRequest request) {
        String actorId = SecurityUtils.getCurrentUserId();
        String actorUsername = SecurityUtils.getCurrentUsername();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        AuditVerificationResultDto result = auditService.verifyIntegrity();

        auditService.recordEvent(
                actorId,
                actorUsername,
                AuditEventType.AUDIT_INTEGRITY_VERIFY,
                "SYSTEM",
                "AUDIT_CHAIN",
                result.isChainValid() ? AuditOutcome.SUCCESS : AuditOutcome.FAILED,
                clientIp,
                userAgent,
                null,
                "Verified audit trail: status=" + result.status() + ", valid=" + result.isChainValid() + ", blocks=" + result.totalEventsChecked()
        );

        return ResponseEntity.ok(ApiResponse.ok("Audit integrity verification completed", result));
    }
}
