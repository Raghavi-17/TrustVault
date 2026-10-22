package com.trustvault.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    public static final String GENESIS_PREV_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "sequence_number", nullable = false, unique = true)
    private Long sequenceNumber;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "timestamp_epoch_ms")
    private Long timestampEpochMs;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_username", length = 50)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 50, nullable = false)
    private AuditEventType action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id", length = 36)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20, nullable = false)
    private AuditOutcome outcome;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "previous_hash", length = 64, nullable = false)
    private String previousHash;

    @Column(name = "current_hash", length = 64, nullable = false)
    private String currentHash;

    public AuditEvent() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public AuditEvent(Long sequenceNumber,
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
                      String currentHash) {
        this.id = UUID.randomUUID().toString();
        this.sequenceNumber = sequenceNumber;
        this.timestamp = Instant.now();
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.details = details;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Instant getTimestamp() {
        if (timestampEpochMs != null) {
            return Instant.ofEpochMilli(timestampEpochMs);
        }
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        if (timestamp != null) {
            this.timestampEpochMs = timestamp.toEpochMilli();
        }
    }

    public Long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public void setTimestampEpochMs(Long timestampEpochMs) {
        this.timestampEpochMs = timestampEpochMs;
        if (timestampEpochMs != null) {
            this.timestamp = Instant.ofEpochMilli(timestampEpochMs);
        }
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public AuditEventType getAction() {
        return action;
    }

    public void setAction(AuditEventType action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AuditOutcome outcome) {
        this.outcome = outcome;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setCurrentHash(String currentHash) {
        this.currentHash = currentHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditEvent that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
