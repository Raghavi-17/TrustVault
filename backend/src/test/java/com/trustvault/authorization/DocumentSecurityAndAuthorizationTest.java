package com.trustvault.authorization;

import com.trustvault.audit.AuditEventRepository;
import com.trustvault.audit.AuditEventType;
import com.trustvault.audit.AuditOutcome;
import com.trustvault.common.AccessForbiddenException;
import com.trustvault.common.AccountDeactivatedException;
import com.trustvault.common.ResourceNotFoundException;
import com.trustvault.document.*;
import com.trustvault.permission.*;
import com.trustvault.storage.StorageService;
import com.trustvault.user.Role;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class DocumentSecurityAndAuthorizationTest {

    @Autowired
    private DocumentAuthorizationService authorizationService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.trustvault.admin.AdminService adminService;

    @Autowired
    private StorageService storageService;

    private User ownerAlice;
    private User viewerCarol;
    private User ownerBob;
    private User adminUser;
    private Document aliceDocument;

    @BeforeEach
    public void setup() {
        permissionRepository.deleteAll();
        documentRepository.deleteAll();
        auditEventRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        ownerAlice = userRepository.save(new User("alice", passwordEncoder.encode("Pass123!"), "Alice Owner", "alice@test.local", Role.OWNER));
        viewerCarol = userRepository.save(new User("carol", passwordEncoder.encode("Pass123!"), "Carol Viewer", "carol@test.local", Role.VIEWER));
        ownerBob = userRepository.save(new User("bob", passwordEncoder.encode("Pass123!"), "Bob Owner", "bob@test.local", Role.OWNER));
        adminUser = userRepository.save(new User("admin", passwordEncoder.encode("Pass123!"), "Admin User", "admin@test.local", Role.ADMIN));

        // Store a real file in storage for aliceDocument
        String storageKey = storageService.store(new MockMultipartFile(
                "file", "budget.pdf", "application/pdf", "Confidential Alice Financial Budget 2026".getBytes()
        ));

        // Alice uploads a private document
        aliceDocument = documentRepository.save(new Document(
                ownerAlice,
                "Alice Confidential Budget",
                "Strictly confidential financial projections",
                null,
                "budget.pdf",
                "budget.pdf",
                storageKey,
                "application/pdf",
                1024L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "finance, confidential"
        ));
    }

    @Test
    @DisplayName("Test 2 & 17: Unauthorized read attempt by Carol is blocked with 403 and logged in audit trail")
    public void testUnauthorizedReadAttemptBlockedAndAudited() {
        assertThrows(AccessForbiddenException.class, () -> {
            authorizationService.authorizeRead(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        });

        // Verify ACCESS_DENIED audit event was generated
        assertEquals(1, auditEventRepository.countByActionAndOutcome(AuditEventType.ACCESS_DENIED, AuditOutcome.DENIED));
    }

    @Test
    @DisplayName("Test 10 & 6: Viewer Carol cannot delete Alice's document")
    public void testViewerCannotDeleteDocument() {
        assertThrows(AccessForbiddenException.class, () -> {
            authorizationService.authorizeOwner(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser", "deletion");
        });
    }

    @Test
    @DisplayName("Test 4: Carol with READ grant cannot perform metadata edits")
    public void testReadGrantCannotEditMetadata() {
        // Grant Carol READ permission
        permissionRepository.save(new DocumentPermission(aliceDocument, viewerCarol, PermissionLevel.READ, ownerAlice));

        // Carol attempts EDIT
        assertThrows(AccessForbiddenException.class, () -> {
            authorizationService.authorizeEdit(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        });
    }

    @Test
    @DisplayName("Test 7: Viewer Carol cannot grant permissions to Bob")
    public void testViewerCannotGrantPermissions() {
        GrantPermissionRequest request = new GrantPermissionRequest(ownerBob.getId(), PermissionLevel.READ);

        assertThrows(AccessForbiddenException.class, () -> {
            permissionService.grantPermission(aliceDocument.getId(), request, viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        });
    }

    @Test
    @DisplayName("Test 11: Immediate revocation takes effect on the next request")
    public void testImmediatePermissionRevocation() {
        // Step 1: Alice grants Carol READ permission
        DocumentPermission grant = permissionRepository.save(new DocumentPermission(aliceDocument, viewerCarol, PermissionLevel.READ, ownerAlice));

        // Step 2: Verify Carol can read
        Document authorizedDoc = authorizationService.authorizeRead(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(authorizedDoc);

        // Step 3: Alice revokes Carol's grant
        permissionService.revokePermission(aliceDocument.getId(), viewerCarol.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // Step 4: Next request by Carol MUST immediately fail with AccessForbiddenException!
        assertThrows(AccessForbiddenException.class, () -> {
            authorizationService.authorizeRead(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        });
    }

    @Test
    @DisplayName("Test 12: Deactivated user is blocked immediately on protected requests")
    public void testDeactivatedUserBlockedImmediately() {
        // Alice deactivates Carol
        viewerCarol.setActive(false);
        userRepository.save(viewerCarol);

        // Carol attempts any protected operation
        assertThrows(AccountDeactivatedException.class, () -> {
            authorizationService.checkActiveUser(viewerCarol.getId());
        });
    }

    @Test
    @DisplayName("Test 9: Search isolation prevents unauthorized document discovery")
    public void testSearchInformationIsolation() {
        // Bob searches accessible documents
        Page<DocumentDto> bobDocs = documentService.getAccessibleDocuments(ownerBob.getId(), PageRequest.of(0, 10));

        // Alice's unshared document must NOT appear in Bob's results
        boolean containsAliceDoc = bobDocs.getContent().stream()
                .anyMatch(d -> d.id().equals(aliceDocument.getId()));
        assertFalse(containsAliceDoc, "Bob must not discover Alice's unshared confidential document");
    }

    @Test
    @DisplayName("Test admin privilege separation: Admin cannot automatically read confidential documents without explicit grant")
    public void testAdminCannotReadWithoutGrant() {
        assertThrows(AccessForbiddenException.class, () -> {
            authorizationService.authorizeRead(aliceDocument.getId(), adminUser.getId(), "10.0.0.1", "AdminBrowser");
        });
    }

    @Test
    @DisplayName("Invalid Document ID: Non-existent document ID throws ResourceNotFoundException")
    public void testInvalidDocumentIdThrowsResourceNotFoundException() {
        assertThrows(ResourceNotFoundException.class, () -> {
            authorizationService.authorizeRead("non-existent-uuid-99999", ownerAlice.getId(), "10.0.0.1", "AliceBrowser");
        });
    }

    @Test
    @DisplayName("Expired Permissions: Expired document permission is rejected immediately")
    public void testExpiredPermissionRejectedImmediately() {
        // Grant Carol permission that expired 1 hour ago
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        DocumentPermission expiredGrant = new DocumentPermission(
                aliceDocument,
                viewerCarol,
                PermissionLevel.READ,
                ownerAlice,
                oneHourAgo
        );
        permissionRepository.save(expiredGrant);

        // Carol attempts to read document with expired grant -> must be forbidden
        assertThrows(AccessForbiddenException.class, () -> {
            authorizationService.authorizeRead(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        });

        // Effective permission must be null (expired)
        String effective = authorizationService.resolveEffectivePermission(aliceDocument, viewerCarol.getId());
        assertNull(effective, "Expired permission must not resolve as active permission");
    }

    @Test
    @DisplayName("Unauthorized Permission Modification: Non-owner cannot update or revoke permissions")
    public void testNonOwnerCannotModifyPermissions() {
        // Alice grants Carol READ permission
        DocumentPermission grant = permissionRepository.save(
                new DocumentPermission(aliceDocument, viewerCarol, PermissionLevel.READ, ownerAlice)
        );

        // Bob (another owner, but NOT this document's owner) attempts to modify Carol's permission
        UpdatePermissionRequest updateReq = new UpdatePermissionRequest(PermissionLevel.EDIT);
        assertThrows(AccessForbiddenException.class, () -> {
            permissionService.updatePermission(aliceDocument.getId(), viewerCarol.getId(), updateReq, ownerBob.getId(), "10.0.0.1", "BobBrowser");
        });

        // Carol attempts to revoke Bob's permission
        assertThrows(AccessForbiddenException.class, () -> {
            permissionService.revokePermission(aliceDocument.getId(), viewerCarol.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        });
    }

    @Test
    @DisplayName("Positive Test: Document owner can read, update metadata, and grant permissions")
    public void testAuthorizedOwnerCanPerformAllOperations() {
        // 1. Owner can authorize read
        Document doc = authorizationService.authorizeRead(aliceDocument.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");
        assertNotNull(doc);
        assertEquals("OWNER", authorizationService.resolveEffectivePermission(doc, ownerAlice.getId()));

        // 2. Owner can authorize edit
        Document editableDoc = authorizationService.authorizeEdit(aliceDocument.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");
        assertNotNull(editableDoc);

        // 3. Owner can grant permissions
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.READ);
        PermissionDto granted = permissionService.grantPermission(aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser");
        assertNotNull(granted);
        assertEquals(PermissionLevel.READ, granted.permission());
    }

    @Test
    @DisplayName("Positive Test: Authorized recipient with READ grant can access and read document")
    public void testAuthorizedRecipientCanDownloadAndRead() {
        // Alice grants Carol READ permission
        permissionRepository.save(new DocumentPermission(aliceDocument, viewerCarol, PermissionLevel.READ, ownerAlice));

        // Carol can read document details
        Document doc = authorizationService.authorizeRead(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(doc);
        assertEquals("READ", authorizationService.resolveEffectivePermission(doc, viewerCarol.getId()));
    }

    @Test
    @DisplayName("Granular Permissions: User with VIEW permission can read metadata but cannot download document content")
    public void testViewOnlyPermissionCannotDownloadDocument() {
        // Alice grants Carol VIEW permission only
        permissionRepository.save(new DocumentPermission(aliceDocument, viewerCarol, PermissionLevel.VIEW, ownerAlice));

        // Carol can read metadata
        Document doc = authorizationService.authorizeRead(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(doc);
        assertEquals("VIEW", authorizationService.resolveEffectivePermission(doc, viewerCarol.getId()));

        // Carol attempting to download must be blocked with 403 AccessForbiddenException
        AccessForbiddenException ex = assertThrows(AccessForbiddenException.class, () ->
                authorizationService.authorizeDownload(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );
        assertTrue(ex.getMessage().contains("VIEW"), "Exception message must state that VIEW permission restricts download");
    }

    @Test
    @DisplayName("Granular Permissions: User with DOWNLOAD permission can download document content")
    public void testDownloadPermissionAllowsDownload() {
        // Alice grants Carol DOWNLOAD permission
        permissionRepository.save(new DocumentPermission(aliceDocument, viewerCarol, PermissionLevel.DOWNLOAD, ownerAlice));

        // Carol can read metadata
        Document doc = authorizationService.authorizeRead(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(doc);

        // Carol can authorize download
        Document downloadDoc = authorizationService.authorizeDownload(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(downloadDoc);
        assertEquals("DOWNLOAD", authorizationService.resolveEffectivePermission(downloadDoc, viewerCarol.getId()));
    }

    @Test
    @DisplayName("Requirement 1: Authorized owner can view details and download their document")
    public void testOwnerCanViewAndDownloadDocument() {
        // Owner views document details
        DocumentDto details = documentService.getDocumentDetails(aliceDocument.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");
        assertNotNull(details);
        assertEquals("OWNER", details.effectivePermission());
        assertEquals("budget.pdf", details.originalFilename());

        // Owner downloads document content
        DocumentService.DownloadPayload payload = documentService.downloadDocument(aliceDocument.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");
        assertNotNull(payload);
        assertEquals("budget.pdf", payload.filename());
        assertNotNull(payload.resource());
        assertTrue(payload.resource().exists());
    }

    @Test
    @DisplayName("Requirement 2: Recipient with VIEW permission can view but cannot download")
    public void testRecipientWithViewPermissionCanViewButCannotDownload() {
        permissionRepository.save(new DocumentPermission(aliceDocument, viewerCarol, PermissionLevel.VIEW, ownerAlice));

        // Carol can view details
        DocumentDto details = documentService.getDocumentDetails(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(details);
        assertEquals("VIEW", details.effectivePermission());

        // Carol calling downloadDocument directly must be rejected with 403 AccessForbiddenException
        AccessForbiddenException ex = assertThrows(AccessForbiddenException.class, () ->
                documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );
        assertTrue(ex.getMessage().contains("VIEW"));
    }

    @Test
    @DisplayName("Requirement 3: Recipient with DOWNLOAD permission can download")
    public void testRecipientWithDownloadPermissionCanDownload() {
        permissionRepository.save(new DocumentPermission(aliceDocument, viewerCarol, PermissionLevel.DOWNLOAD, ownerAlice));

        // Carol downloads document content
        DocumentService.DownloadPayload payload = documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(payload);
        assertEquals("budget.pdf", payload.filename());
        assertTrue(payload.resource().exists());
    }

    @Test
    @DisplayName("Requirement 4: Unauthorized user cannot view or download another user's document")
    public void testUnauthorizedUserCannotViewOrDownloadAnotherUsersDocument() {
        // Bob has no permissions on Alice's document
        assertThrows(AccessForbiddenException.class, () ->
                documentService.getDocumentDetails(aliceDocument.getId(), ownerBob.getId(), "10.0.0.1", "BobBrowser")
        );

        assertThrows(AccessForbiddenException.class, () ->
                documentService.downloadDocument(aliceDocument.getId(), ownerBob.getId(), "10.0.0.1", "BobBrowser")
        );

        // Admin without explicit grant also cannot download confidential document
        assertThrows(AccessForbiddenException.class, () ->
                documentService.downloadDocument(aliceDocument.getId(), adminUser.getId(), "10.0.0.1", "AdminBrowser")
        );
    }

    @Test
    @DisplayName("Requirement 5: Revoked share cannot be used for subsequent access")
    public void testRevokedShareCannotBeUsedForSubsequentAccess() {
        // 1. Grant permission
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.DOWNLOAD);
        permissionService.grantPermission(aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // Carol downloads successfully while grant is active
        DocumentService.DownloadPayload payload = documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(payload);

        // 2. Alice revokes Carol's permission
        permissionService.revokePermission(aliceDocument.getId(), viewerCarol.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // 3. Carol attempts subsequent download -> must immediately fail with 403
        assertThrows(AccessForbiddenException.class, () ->
                documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );

        // Subsequent view attempt also fails
        assertThrows(AccessForbiddenException.class, () ->
                documentService.getDocumentDetails(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );
    }

    @Test
    @DisplayName("Requirement 6: Invalid document IDs and missing files are handled safely with ResourceNotFoundException")
    public void testInvalidDocumentIdAndMissingFileHandledSafely() {
        // Non-existent document ID throws ResourceNotFoundException
        String randomId = java.util.UUID.randomUUID().toString();
        assertThrows(ResourceNotFoundException.class, () ->
                documentService.getDocumentDetails(randomId, ownerAlice.getId(), "10.0.0.1", "AliceBrowser")
        );

        assertThrows(ResourceNotFoundException.class, () ->
                documentService.downloadDocument(randomId, ownerAlice.getId(), "10.0.0.1", "AliceBrowser")
        );

        // Document pointing to non-existent storage key throws ResourceNotFoundException
        Document missingFileDoc = documentRepository.save(new Document(
                ownerAlice, "Ghost Doc", "Ghost file", null, "ghost.pdf", "ghost.pdf",
                "non-existent-storage-key-" + java.util.UUID.randomUUID(),
                "application/pdf", 512L, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "ghost"
        ));

        assertThrows(ResourceNotFoundException.class, () ->
                documentService.downloadDocument(missingFileDoc.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser")
        );
    }

    @Test
    @DisplayName("Requirement 7: Successful downloads create accurate audit records and update real download count")
    public void testSuccessfulDownloadCreatesAuditRecordAndUpdatesDownloadCount() {
        long initialDownloads = adminService.getDashboardStats().totalDownloads();

        // 1. Alice performs a successful download
        DocumentService.DownloadPayload payload = documentService.downloadDocument(aliceDocument.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");
        assertNotNull(payload);

        // Total downloads must increment by exactly 1
        long afterSuccessDownloads = adminService.getDashboardStats().totalDownloads();
        assertEquals(initialDownloads + 1, afterSuccessDownloads, "Successful download must increment totalDownloads stat");

        // Verify audit event exists
        long downloadAuditCount = auditEventRepository.countByActionAndOutcome(AuditEventType.DOCUMENT_DOWNLOAD, AuditOutcome.SUCCESS);
        assertTrue(downloadAuditCount >= 1, "Successful download must be audited with DOCUMENT_DOWNLOAD and SUCCESS");

        // 2. Carol attempts unauthorized download -> fails
        assertThrows(AccessForbiddenException.class, () ->
                documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );

        // Total downloads must NOT increment on failed/unauthorized attempt
        long afterFailureDownloads = adminService.getDashboardStats().totalDownloads();
        assertEquals(afterSuccessDownloads, afterFailureDownloads, "Unauthorized download attempt must NOT increment totalDownloads");
    }

    @Test
    @DisplayName("VIEW Permission E2E: Owner can share with VIEW and recipient can list and inspect document metadata")
    public void testOwnerCanShareDocumentWithViewPermissionAndRecipientCanAccessMetadata() {
        // 1. Owner shares with VIEW permission
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.VIEW);
        PermissionDto granted = permissionService.grantPermission(
                aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser"
        );
        assertNotNull(granted);
        assertEquals(PermissionLevel.VIEW, granted.permission());

        // 2. Database stores exact VIEW permission
        DocumentPermission permRecord = permissionRepository
                .findByDocumentIdAndUserId(aliceDocument.getId(), viewerCarol.getId())
                .orElse(null);
        assertNotNull(permRecord);
        assertEquals(PermissionLevel.VIEW, permRecord.getPermission());

        // 3. Recipient lists accessible documents -> Alice's document is visible with VIEW permission
        Page<DocumentDto> accessibleDocs = documentService.getAccessibleDocuments(viewerCarol.getId(), PageRequest.of(0, 10));
        assertTrue(accessibleDocs.getContent().stream().anyMatch(d -> d.id().equals(aliceDocument.getId())));
        DocumentDto listDto = accessibleDocs.getContent().stream()
                .filter(d -> d.id().equals(aliceDocument.getId()))
                .findFirst().orElseThrow();
        assertEquals("VIEW", listDto.effectivePermission());

        // 4. Recipient views document details -> details and metadata are returned correctly
        DocumentDto details = documentService.getDocumentDetails(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(details);
        assertEquals(aliceDocument.getId(), details.id());
        assertEquals("Alice Confidential Budget", details.title());
        assertEquals("budget.pdf", details.originalFilename());
        assertEquals("VIEW", details.effectivePermission());
    }

    @Test
    @DisplayName("VIEW Permission Restrictions: Backend strictly denies download, edit metadata, and replace content")
    public void testViewPermissionStrictlyRestrictsDownloadEditAndReplaceOnBackend() {
        // Alice grants Carol VIEW permission
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.VIEW);
        permissionService.grantPermission(aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // 1. Download attempt is rejected with 403 AccessForbiddenException
        AccessForbiddenException downloadEx = assertThrows(AccessForbiddenException.class, () ->
                documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );
        assertTrue(downloadEx.getMessage().contains("VIEW"));

        // 2. Edit metadata attempt is rejected with 403 AccessForbiddenException
        DocumentUpdateRequest updateReq = new DocumentUpdateRequest("Tampered Title", "Hacked description", null, "hacked");
        assertThrows(AccessForbiddenException.class, () ->
                documentService.updateMetadata(aliceDocument.getId(), updateReq, viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );

        // 3. Replace content attempt is rejected with 403 AccessForbiddenException
        MockMultipartFile replaceFile = new MockMultipartFile("file", "replacement.pdf", "application/pdf", "Tampered content".getBytes());
        assertThrows(AccessForbiddenException.class, () ->
                documentService.replaceContent(aliceDocument.getId(), replaceFile, viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );

        // 4. Delete attempt is rejected with 403 AccessForbiddenException
        assertThrows(AccessForbiddenException.class, () ->
                documentService.deleteDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );

        // Verify ACCESS_DENIED audit events were recorded for the denied operations
        long deniedCount = auditEventRepository.countByActionAndOutcome(AuditEventType.ACCESS_DENIED, AuditOutcome.DENIED);
        assertTrue(deniedCount >= 4, "Backend must record ACCESS_DENIED audit events for all unauthorized operations");
    }

    @Test
    @DisplayName("VIEW Permission Revocation: Revoking VIEW share immediately terminates all access")
    public void testRevokedViewShareImmediatelyTerminatesAllAccess() {
        // Alice grants Carol VIEW permission
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.VIEW);
        permissionService.grantPermission(aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // Carol can read details while grant is active
        assertNotNull(documentService.getDocumentDetails(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser"));

        // Alice revokes Carol's VIEW permission
        permissionService.revokePermission(aliceDocument.getId(), viewerCarol.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // Immediate revocation: Carol reading details now throws 403 AccessForbiddenException
        assertThrows(AccessForbiddenException.class, () ->
                documentService.getDocumentDetails(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );

        // Carol downloading now throws 403 AccessForbiddenException
        assertThrows(AccessForbiddenException.class, () ->
                documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );

        // Document no longer appears in Carol's accessible documents list
        Page<DocumentDto> accessibleDocs = documentService.getAccessibleDocuments(viewerCarol.getId(), PageRequest.of(0, 10));
        assertFalse(accessibleDocs.getContent().stream().anyMatch(d -> d.id().equals(aliceDocument.getId())));
    }

    @Test
    @DisplayName("Legacy READ Preservation: Existing READ permission continues working for view and download")
    public void testExistingReadPermissionContinuesWorkingForViewAndDownload() {
        // Alice grants Carol legacy READ permission
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.READ);
        permissionService.grantPermission(aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // 1. Carol can view details
        DocumentDto details = documentService.getDocumentDetails(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(details);
        assertEquals("READ", details.effectivePermission());

        // 2. Carol can download original file
        DocumentService.DownloadPayload payload = documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser");
        assertNotNull(payload);
        assertEquals("budget.pdf", payload.filename());
        assertTrue(payload.resource().exists());

        // 3. Carol with READ still CANNOT edit metadata or replace content
        DocumentUpdateRequest updateReq = new DocumentUpdateRequest("Tampered Title", "Hacked description", null, "hacked");
        assertThrows(AccessForbiddenException.class, () ->
                documentService.updateMetadata(aliceDocument.getId(), updateReq, viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );

        MockMultipartFile replaceFile = new MockMultipartFile("file", "replacement.pdf", "application/pdf", "Tampered content".getBytes());
        assertThrows(AccessForbiddenException.class, () ->
                documentService.replaceContent(aliceDocument.getId(), replaceFile, viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );
    }

    @Test
    @DisplayName("Document Preview: Owner can preview document content in-browser")
    public void testOwnerCanPreviewDocumentInBrowser() {
        DocumentService.DownloadPayload preview = documentService.previewDocument(
                aliceDocument.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser"
        );
        assertNotNull(preview);
        assertEquals("budget.pdf", preview.filename());
        assertEquals("application/pdf", preview.mimeType());
        assertNotNull(preview.resource());
        assertTrue(preview.resource().exists());
    }

    @Test
    @DisplayName("Document Preview: Recipient with VIEW permission can preview in-browser but CANNOT download original file")
    public void testViewPermissionAllowsInBrowserPreviewButDeniesDownload() {
        // Alice grants Carol VIEW permission
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.VIEW);
        permissionService.grantPermission(aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // 1. Carol can preview document in-browser
        DocumentService.DownloadPayload preview = documentService.previewDocument(
                aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser"
        );
        assertNotNull(preview);
        assertEquals("budget.pdf", preview.filename());
        assertTrue(preview.resource().exists());

        // 2. Carol calling downloadDocument is strictly blocked with 403
        AccessForbiddenException ex = assertThrows(AccessForbiddenException.class, () ->
                documentService.downloadDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );
        assertTrue(ex.getMessage().contains("VIEW"));
    }

    @Test
    @DisplayName("Document Preview: Recipient with READ permission can both preview in-browser and download original file")
    public void testReadPermissionAllowsBothPreviewAndDownload() {
        // Alice grants Carol READ permission
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.READ);
        permissionService.grantPermission(aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // 1. Preview succeeds
        DocumentService.DownloadPayload preview = documentService.previewDocument(
                aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser"
        );
        assertNotNull(preview);
        assertTrue(preview.resource().exists());

        // 2. Download also succeeds
        DocumentService.DownloadPayload download = documentService.downloadDocument(
                aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser"
        );
        assertNotNull(download);
        assertTrue(download.resource().exists());
    }

    @Test
    @DisplayName("Document Preview: Unauthorized user cannot preview document content")
    public void testUnauthorizedUserCannotPreviewDocument() {
        // Bob has no permission on Alice's document
        assertThrows(AccessForbiddenException.class, () ->
                documentService.previewDocument(aliceDocument.getId(), ownerBob.getId(), "10.0.0.1", "BobBrowser")
        );

        // Admin without explicit grant also cannot preview confidential document
        assertThrows(AccessForbiddenException.class, () ->
                documentService.previewDocument(aliceDocument.getId(), adminUser.getId(), "10.0.0.1", "AdminBrowser")
        );
    }

    @Test
    @DisplayName("Document Preview: Revoked share immediately blocks preview access")
    public void testRevokedShareCannotPreviewDocument() {
        // Alice grants Carol VIEW permission
        GrantPermissionRequest grantReq = new GrantPermissionRequest(viewerCarol.getId(), PermissionLevel.VIEW);
        permissionService.grantPermission(aliceDocument.getId(), grantReq, ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // Carol can preview while active
        assertNotNull(documentService.previewDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser"));

        // Alice revokes Carol's share
        permissionService.revokePermission(aliceDocument.getId(), viewerCarol.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser");

        // Subsequent preview fails immediately with 403
        assertThrows(AccessForbiddenException.class, () ->
                documentService.previewDocument(aliceDocument.getId(), viewerCarol.getId(), "10.0.0.1", "CarolBrowser")
        );
    }

    @Test
    @DisplayName("Document Preview: Missing files and non-existent IDs throw ResourceNotFoundException")
    public void testMissingFileOrInvalidDocumentIdOnPreviewThrowsResourceNotFoundException() {
        // Invalid document ID throws ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () ->
                documentService.previewDocument(java.util.UUID.randomUUID().toString(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser")
        );

        // Missing file on storage throws ResourceNotFoundException
        Document missingFileDoc = documentRepository.save(new Document(
                ownerAlice, "Ghost Doc Preview", "Ghost file", null, "ghost_preview.pdf", "ghost_preview.pdf",
                "missing-storage-key-" + java.util.UUID.randomUUID(),
                "application/pdf", 512L, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "ghost"
        ));

        assertThrows(ResourceNotFoundException.class, () ->
                documentService.previewDocument(missingFileDoc.getId(), ownerAlice.getId(), "10.0.0.1", "AliceBrowser")
        );
    }
}
