package com.trustvault.audit;

import java.time.Instant;

public record AuditVerificationResultDto(
    boolean isChainValid,
    long totalEventsChecked,
    Long brokenSequenceNumber,
    String message,
    Instant verifiedAt
) {}
