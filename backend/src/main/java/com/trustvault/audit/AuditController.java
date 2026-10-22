package com.trustvault.audit;

import com.trustvault.auth.SecurityUtils;
import com.trustvault.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "Query audit trail events with pagination (Admin only)")
    public ResponseEntity<ApiResponse<Page<AuditEventDto>>> getAuditEvents(
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditEventType action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());

        Page<AuditEvent> events;
        if (outcome != null) {
            events = auditService.getEventsByOutcome(outcome, pageRequest);
        } else if (action != null) {
            events = auditService.getEventsByAction(action, pageRequest);
        } else {
            events = auditService.getEvents(pageRequest);
        }

        return ResponseEntity.ok(ApiResponse.ok(events.map(AuditEventDto::fromEntity)));
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
                "Verified audit trail: valid=" + result.isChainValid() + ", blocks=" + result.totalEventsChecked()
        );

        return ResponseEntity.ok(ApiResponse.ok("Audit integrity verification completed", result));
    }
}
