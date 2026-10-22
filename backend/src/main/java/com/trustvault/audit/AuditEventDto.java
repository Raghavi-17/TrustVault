package com.trustvault.audit;

import java.time.Instant;

public record AuditEventDto(
    String id,
    Long sequenceNumber,
    Instant timestamp,
    String actorId,
    String actorUsername,
    AuditEventType action,
    String targetType,
    String targetId,
    AuditOutcome outcome,
    String ipAddress,
    String userAgent,
    String correlationId,
    String details,
    String previousHash,
    String currentHash
) {
    public static AuditEventDto fromEntity(AuditEvent event) {
        return new AuditEventDto(
            event.getId(),
            event.getSequenceNumber(),
            event.getTimestamp(),
            event.getActorId(),
            event.getActorUsername(),
            event.getAction(),
            event.getTargetType(),
            event.getTargetId(),
            event.getOutcome(),
            event.getIpAddress(),
            event.getUserAgent(),
            event.getCorrelationId(),
            event.getDetails(),
            event.getPreviousHash(),
            event.getCurrentHash()
        );
    }
}
