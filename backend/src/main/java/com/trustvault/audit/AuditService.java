package com.trustvault.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Appends an audit event to the tamper-evident hash chain.
     * Uses synchronized execution, REQUIRES_NEW propagation (persists even if outer tx fails),
     * and transaction isolation to prevent chain forks.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public synchronized AuditEvent recordEvent(
            String actorId,
            String actorUsername,
            AuditEventType action,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            String ipAddress,
            String userAgent,
            String correlationId,
            String details) {

        Optional<AuditEvent> lastEventOpt = auditEventRepository.findTopByOrderBySequenceNumberDesc();

        long sequenceNumber = lastEventOpt.map(e -> e.getSequenceNumber() + 1).orElse(1L);
        String previousHash = lastEventOpt.map(AuditEvent::getCurrentHash).orElse(AuditEvent.GENESIS_PREV_HASH);

        // Normalize timestamp to millisecond precision to guarantee DB parity
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String currentHash = computeHash(
                sequenceNumber,
                previousHash,
                now,
                actorId,
                action,
                targetType,
                targetId,
                outcome,
                details
        );

        AuditEvent event = new AuditEvent(
                sequenceNumber,
                actorId,
                actorUsername,
                action,
                targetType,
                targetId,
                outcome,
                ipAddress,
                userAgent,
                correlationId,
                details,
                previousHash,
                currentHash
        );
        event.setTimestamp(now);
        event.setTimestampEpochMs(now.toEpochMilli());

        AuditEvent saved = auditEventRepository.saveAndFlush(event);
        log.info("Audit event recorded: seq={}, action={}, outcome={}, actor={}, currentHash={}",
                sequenceNumber, action, outcome, actorUsername, currentHash);
        return saved;
    }

    /**
     * Deterministically calculates SHA-256 for an audit event block using epoch milliseconds.
     */
    public static String computeHash(
            Long sequenceNumber,
            String previousHash,
            Instant timestamp,
            String actorId,
            AuditEventType action,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            String details) {

        String epochMs = (timestamp != null)
                ? String.valueOf(timestamp.toEpochMilli())
                : "";

        String rawData = String.join("|",
                String.valueOf(sequenceNumber),
                previousHash != null ? previousHash : "",
                epochMs,
                actorId != null ? actorId : "ANONYMOUS",
                action != null ? action.name() : "",
                targetType != null ? targetType : "NONE",
                targetId != null ? targetId : "NONE",
                outcome != null ? outcome.name() : "",
                details != null ? details : ""
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawData.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies the cryptographic integrity of the entire audit chain from genesis to head.
     */
    @Transactional(readOnly = true)
    public AuditVerificationResultDto verifyIntegrity() {
        List<AuditEvent> allEvents = auditEventRepository.findAllByOrderBySequenceNumberAsc();
        Instant now = Instant.now();

        if (allEvents.isEmpty()) {
            return new AuditVerificationResultDto(true, 0, null, "Audit trail is empty. Genesis state is valid.", now);
        }

        String expectedPrevHash = AuditEvent.GENESIS_PREV_HASH;
        long expectedSeq = 1L;

        for (AuditEvent event : allEvents) {
            // Check 1: Strict monotonic sequence numbering
            if (!event.getSequenceNumber().equals(expectedSeq)) {
                String error = String.format("Sequence gap or out-of-order detected. Expected %d, found %d",
                        expectedSeq, event.getSequenceNumber());
                log.error("Audit verification failed: {}", error);
                return new AuditVerificationResultDto(false, allEvents.size(), event.getSequenceNumber(), error, now);
            }

            // Check 2: Previous hash link integrity
            if (!event.getPreviousHash().equals(expectedPrevHash)) {
                String error = String.format("Previous hash mismatch at sequence %d. Expected %s, found %s",
                        event.getSequenceNumber(), expectedPrevHash, event.getPreviousHash());
                log.error("Audit verification failed: {}", error);
                return new AuditVerificationResultDto(false, allEvents.size(), event.getSequenceNumber(), error, now);
            }

            // Check 3: Current hash deterministic recalculation
            String calculatedHash = computeHash(
                    event.getSequenceNumber(),
                    event.getPreviousHash(),
                    event.getTimestamp(),
                    event.getActorId(),
                    event.getAction(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getOutcome(),
                    event.getDetails()
            );

            if (!event.getCurrentHash().equals(calculatedHash)) {
                String error = String.format("Current hash recalculation mismatch at sequence %d. Data tampering detected! Stored=%s, Recomputed=%s",
                        event.getSequenceNumber(), event.getCurrentHash(), calculatedHash);
                log.error("Audit verification failed: {}", error);
                return new AuditVerificationResultDto(false, allEvents.size(), event.getSequenceNumber(), error, now);
            }

            expectedPrevHash = event.getCurrentHash();
            expectedSeq++;
        }

        log.info("Audit verification passed: all {} events in chain verified successfully.", allEvents.size());
        return new AuditVerificationResultDto(true, allEvents.size(), null,
                "Audit trail cryptographic integrity verified. All " + allEvents.size() + " blocks valid.", now);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> getEvents(Pageable pageable) {
        return auditEventRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> getEventsByOutcome(AuditOutcome outcome, Pageable pageable) {
        return auditEventRepository.findByOutcomeOrderByTimestampDesc(outcome, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> getEventsByAction(AuditEventType action, Pageable pageable) {
        return auditEventRepository.findByActionOrderByTimestampDesc(action, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> getRecentSecurityActivity() {
        return auditEventRepository.findTop10ByOrderByTimestampDesc();
    }
}
