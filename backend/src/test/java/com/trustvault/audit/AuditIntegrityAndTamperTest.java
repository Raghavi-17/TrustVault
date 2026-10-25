package com.trustvault.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.ActiveProfiles;

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
        auditService.resetCheckpoints();
        auditEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Valid Chain: Untampered audit trail starts from genesis and passes verification with CHAIN_INTACT")
    public void testValidChainIntegrity() {
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

        assertEquals(AuditEvent.GENESIS_PREV_HASH, event1.getPreviousHash());
        assertEquals(event1.getCurrentHash(), event2.getPreviousHash());
        assertEquals(event2.getCurrentHash(), event3.getPreviousHash());

        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INTACT", result.status());
        assertTrue(result.isChainValid(), "Chain must be cryptographically valid");
        assertEquals(3, result.totalEventsChecked());
        assertNull(result.brokenSequenceNumber());
        assertTrue(result.message().contains("cryptographic integrity verified"));
    }

    @Test
    @DisplayName("Tampered Record Details: Verification detects modified record details and returns CHAIN_INVALID")
    public void testTamperedRecordDetailsDetected() {
        auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );
        AuditEvent event2 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.DOCUMENT_UPLOAD, "DOCUMENT", "doc-100",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-2", "Original doc upload"
        );
        auditService.recordEvent(
                "user-2", "bob", AuditEventType.ACCESS_DENIED, "DOCUMENT", "doc-100",
                AuditOutcome.DENIED, "127.0.0.1", "TestAgent", "corr-3", "Denied read"
        );

        // Tamper with details in event 2
        event2.setDetails("FORGED DETAILS BY MALICIOUS DB OPERATOR");
        auditEventRepository.saveAndFlush(event2);

        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result.status());
        assertFalse(result.isChainValid());
        assertEquals(2L, result.brokenSequenceNumber());
        assertTrue(result.message().contains("data tampering detected"));
    }

    @Test
    @DisplayName("Tampered Record Fields: Verification detects modified canonical fields (actor/target/action) and returns CHAIN_INVALID")
    public void testTamperedRecordFieldsDetected() {
        AuditEvent event1 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );
        auditService.recordEvent(
                "user-1", "alice", AuditEventType.DOCUMENT_UPLOAD, "DOCUMENT", "doc-100",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-2", "Doc upload"
        );

        // Tamper with target ID in event 1
        event1.setTargetId("FORGED-TARGET-999");
        auditEventRepository.saveAndFlush(event1);

        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result.status());
        assertFalse(result.isChainValid());
        assertEquals(1L, result.brokenSequenceNumber());
        assertTrue(result.message().contains("data tampering detected"));
    }

    @Test
    @DisplayName("Modified Stored Hash: Verification detects modified current_hash and returns CHAIN_INVALID")
    public void testModifiedStoredCurrentHashDetected() {
        auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );
        AuditEvent event2 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.DOCUMENT_UPLOAD, "DOCUMENT", "doc-100",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-2", "Original doc upload"
        );

        // Stored hash modified to another valid 64-char hex string
        event2.setCurrentHash("1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff");
        auditEventRepository.saveAndFlush(event2);

        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result.status());
        assertFalse(result.isChainValid());
        assertEquals(2L, result.brokenSequenceNumber());
        assertTrue(result.message().contains("Current hash recalculation mismatch"));
    }

    @Test
    @DisplayName("Broken Previous-Hash Link: Verification detects mismatch with preceding record hash and returns CHAIN_INVALID")
    public void testBrokenPreviousHashLinkDetected() {
        auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );
        auditService.recordEvent(
                "user-1", "alice", AuditEventType.DOCUMENT_UPLOAD, "DOCUMENT", "doc-100",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-2", "Original doc upload"
        );
        AuditEvent event3 = auditService.recordEvent(
                "user-2", "bob", AuditEventType.ACCESS_DENIED, "DOCUMENT", "doc-100",
                AuditOutcome.DENIED, "127.0.0.1", "TestAgent", "corr-3", "Denied read"
        );

        // Tamper with previousHash link on event 3
        event3.setPreviousHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        auditEventRepository.saveAndFlush(event3);

        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result.status());
        assertFalse(result.isChainValid());
        assertEquals(3L, result.brokenSequenceNumber());
        assertTrue(result.message().contains("Previous hash mismatch"));
    }

    @Test
    @DisplayName("Broken Genesis Previous Hash: Verification detects non-genesis previous_hash on block 1")
    public void testBrokenGenesisPreviousHashDetected() {
        AuditEvent event1 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );

        event1.setPreviousHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        auditEventRepository.saveAndFlush(event1);

        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result.status());
        assertFalse(result.isChainValid());
        assertEquals(1L, result.brokenSequenceNumber());
        assertTrue(result.message().contains("Previous hash mismatch"));
    }

    @Test
    @DisplayName("Empty Chain: Empty audit repository returns CHAIN_INTACT with 0 events checked")
    public void testEmptyChainHandling() {
        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INTACT", result.status());
        assertTrue(result.isChainValid());
        assertEquals(0, result.totalEventsChecked());
        assertNull(result.brokenSequenceNumber());
        assertTrue(result.message().contains("empty"));
    }

    @Test
    @DisplayName("Malformed Hash: Verification detects malformed hashes (non-hex or wrong length) and returns CHAIN_INVALID")
    public void testMalformedHashDetected() {
        AuditEvent event1 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );

        // Current hash is too short / malformed
        event1.setCurrentHash("short-invalid-hash");
        auditEventRepository.saveAndFlush(event1);

        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result.status());
        assertFalse(result.isChainValid());
        assertEquals(1L, result.brokenSequenceNumber());
        assertTrue(result.message().contains("Malformed current hash"));

        // Previous hash is malformed non-hex string
        event1.setCurrentHash(AuditEvent.GENESIS_PREV_HASH);
        event1.setPreviousHash("not-a-valid-hex-64-character-string-at-all-xyz-xyz-xyz-xyz-xyz-!");
        auditEventRepository.saveAndFlush(event1);

        AuditVerificationResultDto result2 = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result2.status());
        assertFalse(result2.isChainValid());
        assertEquals(1L, result2.brokenSequenceNumber());
        assertTrue(result2.message().contains("Malformed previous hash"));
    }

    @Test
    @DisplayName("Sequence Gap: Verification detects deleted block or out-of-order sequence and returns CHAIN_INVALID")
    public void testSequenceGapDetected() {
        auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );
        AuditEvent event2 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.DOCUMENT_UPLOAD, "DOCUMENT", "doc-100",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-2", "Doc upload"
        );
        auditService.recordEvent(
                "user-2", "bob", AuditEventType.ACCESS_DENIED, "DOCUMENT", "doc-100",
                AuditOutcome.DENIED, "127.0.0.1", "TestAgent", "corr-3", "Denied read"
        );

        // Delete sequence 2 to create a gap (1 -> 3)
        auditEventRepository.delete(event2);
        auditEventRepository.flush();

        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result.status());
        assertFalse(result.isChainValid());
        assertEquals(2L, result.brokenSequenceNumber());
        assertTrue(result.message().contains("Sequence gap or missing link detected"));
    }

    @Test
    @DisplayName("Verification Error: Database exceptions return VERIFICATION_ERROR without crashing")
    public void testVerificationErrorOnDatabaseFailure() {
        AuditEventRepository mockRepo = Mockito.mock(AuditEventRepository.class);
        Mockito.when(mockRepo.findAllByOrderBySequenceNumberAsc())
                .thenThrow(new CannotAcquireLockException("Database connection timeout"));

        AuditService failingService = new AuditService(mockRepo);
        AuditVerificationResultDto result = failingService.verifyIntegrity();

        assertEquals("VERIFICATION_ERROR", result.status());
        assertFalse(result.isChainValid());
        assertEquals(0, result.totalEventsChecked());
        assertNull(result.brokenSequenceNumber());
        assertTrue(result.message().contains("Database error"));
    }

    @Test
    @DisplayName("Read-Only Guarantee: Verification does not modify, repair, or rewrite tampered audit records")
    public void testVerificationIsReadOnlyAndDoesNotRepairOrRewrite() {
        auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "User logged in"
        );
        AuditEvent event2 = auditService.recordEvent(
                "user-1", "alice", AuditEventType.DOCUMENT_UPLOAD, "DOCUMENT", "doc-100",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-2", "Doc upload"
        );

        event2.setDetails("UNAUTHORIZED_MUTATION");
        auditEventRepository.saveAndFlush(event2);

        // Run verification
        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INVALID", result.status());

        // Verify entity in DB was untouched and NOT repaired
        AuditEvent fetched = auditEventRepository.findById(event2.getId()).orElseThrow();
        assertEquals("UNAUTHORIZED_MUTATION", fetched.getDetails(), "Details must not be rewritten");
        assertEquals(event2.getCurrentHash(), fetched.getCurrentHash(), "Hash must not be silently recalculated");
    }

    @Test
    @DisplayName("Concurrent Operations: High-concurrency audit writes maintain strict sequential order and valid hash chain")
    public void testConcurrentAuditWritesMaintainHashChainIntegrity() throws InterruptedException {
        int threadCount = 6;
        int operationsPerThread = 5;
        int totalOperations = threadCount * operationsPerThread;

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(totalOperations);

        for (int i = 0; i < totalOperations; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    auditService.recordEvent(
                            "concurrent-user-" + (index % threadCount),
                            "user" + (index % threadCount),
                            AuditEventType.DOCUMENT_READ,
                            "DOCUMENT",
                            "doc-" + index,
                            AuditOutcome.SUCCESS,
                            "127.0.0.1",
                            "ConcurrentAgent",
                            "corr-" + index,
                            "Concurrent audit event #" + index
                    );
                } catch (Exception e) {
                    fail("Concurrent audit write threw exception: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Unleash all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(completed, "All concurrent audit operations should complete within timeout");

        // Verify that the whole chain is cryptographically intact and all events are accounted for
        AuditVerificationResultDto result = auditService.verifyIntegrity();
        assertEquals("CHAIN_INTACT", result.status());
        assertTrue(result.isChainValid(), "Concurrent chain must be strictly cryptographically valid without forks");
        assertEquals(totalOperations, result.totalEventsChecked());
        assertNull(result.brokenSequenceNumber());
    }

    @Test
    @DisplayName("Audit History Filtering: Filters correctly by user, action, document, date, and outcome")
    public void testAuditHistoryFilteringByUserActionDocumentOutcomeDate() {
        auditService.recordEvent(
                "user-alice", "alice", AuditEventType.DOCUMENT_UPLOAD, "DOCUMENT", "doc-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "Agent", "corr-1", "Alice uploaded confidential doc-1"
        );
        auditService.recordEvent(
                "user-carol", "carol", AuditEventType.DOCUMENT_DOWNLOAD, "DOCUMENT", "doc-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "Agent", "corr-2", "Carol downloaded doc-1"
        );
        auditService.recordEvent(
                "user-carol", "carol", AuditEventType.ACCESS_DENIED, "DOCUMENT", "doc-2",
                AuditOutcome.DENIED, "127.0.0.1", "Agent", "corr-3", "Carol denied access to doc-2"
        );
        auditService.recordEvent(
                "user-admin", "admin", AuditEventType.CATEGORY_CREATE, "CATEGORY", "cat-99",
                AuditOutcome.SUCCESS, "127.0.0.1", "Agent", "corr-4", "Admin created category cat-99"
        );

        org.springframework.data.domain.PageRequest page = org.springframework.data.domain.PageRequest.of(0, 10);

        // 1. Filter by user (alice)
        var aliceEvents = auditService.getFilteredEvents(null, null, "alice", null, null, null, page);
        assertEquals(1, aliceEvents.getTotalElements());
        assertEquals("alice", aliceEvents.getContent().get(0).getActorUsername());

        // 2. Filter by user (carol)
        var carolEvents = auditService.getFilteredEvents(null, null, "carol", null, null, null, page);
        assertEquals(2, carolEvents.getTotalElements());

        // 3. Filter by outcome (DENIED)
        var deniedEvents = auditService.getFilteredEvents(AuditOutcome.DENIED, null, null, null, null, null, page);
        assertEquals(1, deniedEvents.getTotalElements());
        assertEquals(AuditEventType.ACCESS_DENIED, deniedEvents.getContent().get(0).getAction());

        // 4. Filter by document (doc-1)
        var doc1Events = auditService.getFilteredEvents(null, null, null, "doc-1", null, null, page);
        assertEquals(2, doc1Events.getTotalElements());

        // 5. Composite filter (carol + SUCCESS)
        var carolSuccess = auditService.getFilteredEvents(AuditOutcome.SUCCESS, null, "carol", null, null, null, page);
        assertEquals(1, carolSuccess.getTotalElements());
        assertEquals(AuditEventType.DOCUMENT_DOWNLOAD, carolSuccess.getContent().get(0).getAction());

        // 6. Action filter (CATEGORY_CREATE)
        var catEvents = auditService.getFilteredEvents(null, AuditEventType.CATEGORY_CREATE, null, null, null, null, page);
        assertEquals(1, catEvents.getTotalElements());
        assertEquals("cat-99", catEvents.getContent().get(0).getTargetId());
    }
}
