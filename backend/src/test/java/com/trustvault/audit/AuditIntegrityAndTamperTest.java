package com.trustvault.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AuditIntegrityAndTamperTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    public void setup() {
        auditEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Test 19 & 17: Audit trail starts with genesis block, chains SHA-256 hashes, and detects data tampering")
    public void testHashChainAndTamperDetection() {
        // Step 1: Record 3 consecutive audit events
        AuditEvent event1 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );

        AuditEvent event2 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.DOCUMENT_UPLOAD, "DOCUMENT", "doc-100",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-2", "Uploaded sensitive doc"
        );

        AuditEvent event3 = auditService.recordEvent(
                "user-2", "bob", AuditEventType.ACCESS_DENIED, "DOCUMENT", "doc-100",
                AuditOutcome.DENIED, "127.0.0.1", "TestAgent", "corr-3", "Denied unauthorized read"
        );

        assertEquals(1L, event1.getSequenceNumber());
        assertEquals(2L, event2.getSequenceNumber());
        assertEquals(3L, event3.getSequenceNumber());

        // Check hash linking
        assertEquals(AuditEvent.GENESIS_PREV_HASH, event1.getPreviousHash());
        assertEquals(event1.getCurrentHash(), event2.getPreviousHash());
        assertEquals(event2.getCurrentHash(), event3.getPreviousHash());

        // Step 2: Verify integrity passes on untampered chain
        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertTrue(result.isChainValid(), "Chain must be cryptographically valid. Reason: " + result.message());
        assertEquals(3, result.totalEventsChecked());
        assertNull(result.brokenSequenceNumber());

        // Step 3: Tamper with event 2 in the database (simulate unauthorized row modification)
        event2.setDetails("TAMPERED DETAILS BY MALICIOUS DB OPERATOR");
        auditEventRepository.saveAndFlush(event2);

        // Step 4: Verify integrity detects tampering!
        AuditVerificationResultDto tamperedResult = auditService.verifyIntegrity();
        assertFalse(tamperedResult.isChainValid(), "Verification must detect tampering when row is modified");
        assertEquals(2L, tamperedResult.brokenSequenceNumber(), "Must pinpoint the tampered sequence number");
        assertTrue(tamperedResult.message().contains("Data tampering detected"), "Message must explain tampering");
    }
}
