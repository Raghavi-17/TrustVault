package com.trustvault.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record AuditVerificationResultDto(
    @JsonProperty("status") String status,
    @JsonProperty("verificationStatus") String verificationStatus,
    @JsonProperty("isChainValid") boolean isChainValid,
    @JsonProperty("totalEventsChecked") long totalEventsChecked,
    @JsonProperty("integrityErrorsDetected") long integrityErrorsDetected,
    @JsonProperty("brokenSequenceNumber") Long brokenSequenceNumber,
    @JsonProperty("affectedEventIds") List<String> affectedEventIds,
    @JsonProperty("lastSuccessfulVerification") Instant lastSuccessfulVerification,
    @JsonProperty("message") String message,
    @JsonProperty("securityDisclaimer") String securityDisclaimer,
    @JsonProperty("verifiedAt") Instant verifiedAt
) {
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_ISSUES_DETECTED = "ISSUES DETECTED";
    public static final String STATUS_VERIFICATION_FAILED = "VERIFICATION FAILED";

    public static final String DISCLAIMER = 
        "Cryptographic hash chaining provides tamper evidence against unauthorized modification, deletion, and insertion. Note: A hash chain stored entirely in one database does not independently prevent a privileged database administrator with full access from regenerating an entire chain. Periodic signed checkpoints provide secondary independent verification.";

    public AuditVerificationResultDto(boolean isChainValid, long totalEventsChecked, Long brokenSequenceNumber, String message, Instant verifiedAt) {
        this(
            isChainValid ? "CHAIN_INTACT" : "CHAIN_INVALID",
            isChainValid ? STATUS_VERIFIED : STATUS_ISSUES_DETECTED,
            isChainValid,
            totalEventsChecked,
            isChainValid ? 0L : 1L,
            brokenSequenceNumber,
            brokenSequenceNumber != null ? List.of(String.valueOf(brokenSequenceNumber)) : Collections.emptyList(),
            isChainValid ? verifiedAt : null,
            message,
            DISCLAIMER,
            verifiedAt
        );
    }

    public static AuditVerificationResultDto intact(long totalEvents, String message, Instant verifiedAt) {
        return intact(totalEvents, message, verifiedAt, verifiedAt);
    }

    public static AuditVerificationResultDto intact(long totalEvents, String message, Instant verifiedAt, Instant lastSuccess) {
        return new AuditVerificationResultDto(
            "CHAIN_INTACT",
            STATUS_VERIFIED,
            true,
            totalEvents,
            0L,
            null,
            Collections.emptyList(),
            lastSuccess != null ? lastSuccess : verifiedAt,
            message,
            DISCLAIMER,
            verifiedAt
        );
    }

    public static AuditVerificationResultDto invalid(long totalEvents, Long brokenSequenceNumber, String message, Instant verifiedAt) {
        return invalid(totalEvents, brokenSequenceNumber, message, verifiedAt, null);
    }

    public static AuditVerificationResultDto invalid(long totalEvents, Long brokenSequenceNumber, String message, Instant verifiedAt, Instant lastSuccess) {
        List<String> affected = brokenSequenceNumber != null ? List.of(String.valueOf(brokenSequenceNumber)) : Collections.emptyList();
        return new AuditVerificationResultDto(
            "CHAIN_INVALID",
            STATUS_ISSUES_DETECTED,
            false,
            totalEvents,
            1L,
            brokenSequenceNumber,
            affected,
            lastSuccess,
            message,
            DISCLAIMER,
            verifiedAt
        );
    }

    public static AuditVerificationResultDto error(String message, Instant verifiedAt) {
        return error(message, verifiedAt, null);
    }

    public static AuditVerificationResultDto error(String message, Instant verifiedAt, Instant lastSuccess) {
        return new AuditVerificationResultDto(
            "VERIFICATION_ERROR",
            STATUS_VERIFICATION_FAILED,
            false,
            0L,
            1L,
            null,
            Collections.emptyList(),
            lastSuccess,
            message,
            DISCLAIMER,
            verifiedAt
        );
    }
}
