package com.trustvault.authorization;

import com.trustvault.audit.AuditEventRepository;
import com.trustvault.audit.AuditEventType;
import com.trustvault.audit.AuditOutcome;
import com.trustvault.common.AccessForbiddenException;
import com.trustvault.common.AccountDeactivatedException;
import com.trustvault.document.*;
import com.trustvault.permission.*;
import com.trustvault.storage.StorageService;
import com.trustvault.user.Role;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
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

        // Alice uploads a private document
        aliceDocument = documentRepository.save(new Document(
                ownerAlice,
                "Alice Confidential Budget",
                "Strictly confidential financial projections",
                null,
                "budget.pdf",
                "budget.pdf",
                "storage-key-12345",
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
}
