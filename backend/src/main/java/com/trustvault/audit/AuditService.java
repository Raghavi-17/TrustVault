package com.trustvault.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    @Value("${trustvault.audit.checkpoint-interval:25}")
    private int checkpointInterval = 25;

    @Value("${trustvault.audit.checkpoint-dir:./storage/checkpoints}")
    private String checkpointDirPath = "./storage/checkpoints";

    private static final String CHECKPOINT_SECRET = "TrustVault-Cryptographic-Checkpoint-Key-2026";

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

        // Record periodic signed checkpoint if due
        recordCheckpointIfDue(sequenceNumber, currentHash, now);

        return saved;
    }

    /**
     * Persists periodic signed checkpoint to external file storage to protect against total database rewrites.
     */
    public void recordCheckpointIfDue(long sequenceNumber, String currentHash, Instant timestamp) {
        if (sequenceNumber % checkpointInterval != 0) {
            return;
        }
        try {
            Path dir = Paths.get(checkpointDirPath).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path checkpointFile = dir.resolve("audit_checkpoints.log");

            String canonical = sequenceNumber + "|" + currentHash + "|" + timestamp.toEpochMilli();
            String signature = calculateHmacSha256(canonical, CHECKPOINT_SECRET);
            String line = canonical + "|" + signature + System.lineSeparator();

            Files.writeString(checkpointFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Recorded signed audit checkpoint for block #{}", sequenceNumber);
        } catch (Exception e) {
            log.warn("Could not persist external audit checkpoint for sequence {}: {}", sequenceNumber, e.getMessage());
        }
    }

    /**
     * Resets external checkpoint log (used primarily in test fixture teardowns or administrative resets).
     */
    public synchronized void resetCheckpoints() {
        try {
            Path file = Paths.get(checkpointDirPath).toAbsolutePath().normalize().resolve("audit_checkpoints.log");
            Files.deleteIfExists(file);
        } catch (Exception e) {
            log.warn("Could not delete audit checkpoints file: {}", e.getMessage());
        }
    }

    public static String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
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

    public static final String SHA256_HEX_REGEX = "^[0-9a-fA-F]{64}$";
    private static final java.util.regex.Pattern SHA256_HEX_PATTERN =
            java.util.regex.Pattern.compile(SHA256_HEX_REGEX);

    public static boolean isValidSha256Hex(String hash) {
        return hash != null && SHA256_HEX_PATTERN.matcher(hash).matches();
    }

    /**
     * Verifies the cryptographic integrity of the entire audit chain from genesis to head.
     * This operation is strictly read-only and will never create, repair, or rewrite audit records.
     */
    @Transactional(readOnly = true)
    public AuditVerificationResultDto verifyIntegrity() {
        Instant now = Instant.now();
        Instant lastSuccess = null;
        try {
            lastSuccess = auditEventRepository.findTopByActionAndOutcomeOrderByTimestampDesc(
                    AuditEventType.AUDIT_INTEGRITY_VERIFY, AuditOutcome.SUCCESS
            ).map(AuditEvent::getTimestamp).orElse(null);
        } catch (Exception e) {
            log.debug("Could not query last successful verification timestamp: {}", e.getMessage());
        }

        List<AuditEvent> allEvents;
        try {
            allEvents = auditEventRepository.findAllByOrderBySequenceNumberAsc();
        } catch (Exception e) {
            String errorMsg = "Database error during audit verification: " + e.getMessage();
            log.error(errorMsg, e);
            return AuditVerificationResultDto.error(errorMsg, now, lastSuccess);
        }

        if (allEvents == null || allEvents.isEmpty()) {
            return AuditVerificationResultDto.intact(0, "Audit trail is empty. Genesis state is valid.", now, lastSuccess != null ? lastSuccess : now);
        }

        String expectedPrevHash = AuditEvent.GENESIS_PREV_HASH;
        long expectedSeq = 1L;

        for (AuditEvent event : allEvents) {
            Long currentSeq = event.getSequenceNumber();

            // Check 1: Monotonic sequence numbering and missing link / gap detection
            if (currentSeq == null || currentSeq != expectedSeq) {
                String error = String.format("Sequence gap or missing link detected. Expected sequence %d, found %s",
                        expectedSeq, (currentSeq != null ? String.valueOf(currentSeq) : "null"));
                log.error("Audit verification failed: {}", error);
                return AuditVerificationResultDto.invalid(allEvents.size(), expectedSeq, error, now, lastSuccess);
            }

            // Check 2: Malformed hash detection on previous_hash and current_hash
            if (!isValidSha256Hex(event.getPreviousHash())) {
                String error = String.format("Malformed previous hash at sequence %d: stored previous hash '%s' is not a valid 64-character hexadecimal SHA-256 hash.",
                        currentSeq, event.getPreviousHash());
                log.error("Audit verification failed: {}", error);
                return AuditVerificationResultDto.invalid(allEvents.size(), currentSeq, error, now, lastSuccess);
            }

            if (!isValidSha256Hex(event.getCurrentHash())) {
                String error = String.format("Malformed current hash at sequence %d: stored current hash '%s' is not a valid 64-character hexadecimal SHA-256 hash.",
                        currentSeq, event.getCurrentHash());
                log.error("Audit verification failed: {}", error);
                return AuditVerificationResultDto.invalid(allEvents.size(), currentSeq, error, now, lastSuccess);
            }

            // Check 3: Previous hash chain link match
            if (!event.getPreviousHash().equalsIgnoreCase(expectedPrevHash)) {
                String error = String.format("Previous hash mismatch at sequence %d: broken chain link. Expected %s, found %s",
                        currentSeq, expectedPrevHash, event.getPreviousHash());
                log.error("Audit verification failed: {}", error);
                return AuditVerificationResultDto.invalid(allEvents.size(), currentSeq, error, now, lastSuccess);
            }

            // Check 4: Deterministic recalculation from exact canonical fields
            String calculatedHash;
            try {
                calculatedHash = computeHash(
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
            } catch (Exception e) {
                String error = String.format("Error computing hash at sequence %d: %s", currentSeq, e.getMessage());
                log.error(error, e);
                return AuditVerificationResultDto.error(error, now, lastSuccess);
            }

            if (!event.getCurrentHash().equalsIgnoreCase(calculatedHash)) {
                String error = String.format("Current hash recalculation mismatch at sequence %d: data tampering detected! Stored=%s, Recomputed=%s",
                        currentSeq, event.getCurrentHash(), calculatedHash);
                log.error("Audit verification failed: {}", error);
                return AuditVerificationResultDto.invalid(allEvents.size(), currentSeq, error, now, lastSuccess);
            }

            expectedPrevHash = event.getCurrentHash();
            expectedSeq++;
        }

        // Check 5: Verify secondary external signed checkpoints if present
        try {
            Path checkpointFile = Paths.get(checkpointDirPath).resolve("audit_checkpoints.log");
            if (Files.exists(checkpointFile)) {
                List<String> lines = Files.readAllLines(checkpointFile);
                Map<Long, String> chainMap = allEvents.stream()
                        .collect(Collectors.toMap(AuditEvent::getSequenceNumber, AuditEvent::getCurrentHash, (a, b) -> a));

                for (String line : lines) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\\|");
                    if (parts.length >= 4) {
                        long cpSeq = Long.parseLong(parts[0]);
                        String cpHash = parts[1];
                        String cpSig = parts[3];
                        String expectedSig = calculateHmacSha256(parts[0] + "|" + parts[1] + "|" + parts[2], CHECKPOINT_SECRET);
                        if (!cpSig.equalsIgnoreCase(expectedSig)) {
                            String error = String.format("Signed checkpoint signature invalid for block #%d: checkpoint file tampering detected!", cpSeq);
                            log.error(error);
                            return AuditVerificationResultDto.invalid(allEvents.size(), cpSeq, error, now, lastSuccess);
                        }
                        String actualChainHash = chainMap.get(cpSeq);
                        if (actualChainHash == null || !actualChainHash.equalsIgnoreCase(cpHash)) {
                            String error = String.format("External signed checkpoint mismatch at block #%d! Checkpoint hash=%s, Database hash=%s. Database history rewrite detected!",
                                    cpSeq, cpHash, actualChainHash);
                            log.error(error);
                            return AuditVerificationResultDto.invalid(allEvents.size(), cpSeq, error, now, lastSuccess);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Secondary checkpoint inspection encountered a non-fatal warning: {}", e.getMessage());
        }

        log.info("Audit verification passed: all {} events in chain verified successfully.", allEvents.size());
        return AuditVerificationResultDto.intact(allEvents.size(),
                "Audit trail cryptographic integrity verified. All " + allEvents.size() + " blocks valid.", now, now);
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
    public Page<AuditEvent> getFilteredEvents(
            AuditOutcome outcome,
            AuditEventType action,
            String user,
            String document,
            Instant startDate,
            Instant endDate,
            Pageable pageable) {
        String cleanUser = (user != null && !user.isBlank()) ? user.trim() : null;
        String cleanDoc = (document != null && !document.isBlank()) ? document.trim() : null;
        return auditEventRepository.findFilteredEvents(outcome, action, cleanUser, cleanDoc, startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> getRecentSecurityActivity() {
        return auditEventRepository.findTop10ByOrderByTimestampDesc();
    }
}
