# TRUSTVAULT THREAT MODEL AND APPLICATION SECURITY ANALYSIS

This document defines the formal threat model, asset taxonomy, trust boundaries, threat actors, attack scenarios, implemented mitigations, automated verification tests, residual risks, and production hardening recommendations for **TrustVault — Secure Document Sharing with a Tamper-Evident Audit Trail**.

---

## 1. ASSETS BEING PROTECTED

1. **Confidential Document Payloads**: Uploaded contracts, financial statements, trade secrets, and proprietary files stored in private file storage.
2. **Access Control Integrity**: Explicit per-document grant lists (OWNER, READ, EDIT) ensuring only authorized subjects can view, modify, or download documents.
3. **Audit Trail Immutability**: Cryptographic SHA-256 hash-chain records representing the true chronological history of all security events, authentication attempts, permissions, and administrative operations.
4. **User Credentials & Session Identifiers**: BCrypt-hashed passwords, server-side HTTP-only session cookies (`TRUSTVAULT_SESSION`), and CSRF tokens.
5. **System Identity & Privilege Separation**: Application roles (ADMIN, OWNER, VIEWER) ensuring administrators cannot view confidential document contents merely by virtue of their administrative title.

---

## 2. TRUST BOUNDARIES

```
+--------------------------------------------------------------------------------+
| CLIENT TIER (Untrusted Browser Environment)                                    |
|   React SPA | LocalStorage Theme Preference | User Input Forms                 |
+--------------------------------------------------------------------------------+
                                       |
                              HTTPS / SameSite Session Cookie
                              CSRF Header: X-XSRF-TOKEN
                                       v
+--------------------------------------------------------------------------------+
| INGRESS & APPLICATION TIER (Trusted Boundary)                                  |
|   Spring Security Filter Chain (CORS, CSRF, SessionManagement)                |
|   Rate Limiter (5 attempts / 15-minute sliding window)                         |
|   Central DocumentAuthorizationService (Strict server-side validation)        |
|   FileValidationService (Apache Tika Magic Bytes + Extension Allowlist)       |
+--------------------------------------------------------------------------------+
                       |                                       |
          Private Local/S3 Storage API             JDBC / Hibernate ORM
                       v                                       v
+-------------------------------------+  +---------------------------------------+
| PRIVATE DOCUMENT STORAGE (Isolated) |  | POSTGRESQL DATABASE (Persistent State)|
|   Server-generated UUID storage keys|  |   Users, Documents, Permissions       |
|   Outside web server public root   |  |   Tamper-Evident Audit Trail (Chained)|
+-------------------------------------+  +---------------------------------------+
```

---

## 3. THREAT ACTORS

- **External Malicious Actor**: Internet-based adversary attempting credential brute-forcing, cross-site scripting (XSS), cross-site request forgery (CSRF), or unauthenticated document extraction.
- **Unauthorized Authenticated User (VIEWER / Rogue User)**: Authenticated user attempting Broken Object Level Authorization (BOLA/IDOR), vertical privilege escalation (attempting admin actions), or lateral privilege escalation (reading documents not shared with them).
- **Revoked User**: Formerly authorized collaborator attempting to download cached copies or exploit stale permissions after access has been revoked.
- **Malicious Administrator**: Legitimate admin attempting to abuse administrative powers to inspect confidential documents uploaded by other departments.
- **Database Intruder / Malicious DBA**: Adversary with database access attempting to silently edit past audit records or delete incriminating evidence.

---

## 4. ATTACK SCENARIOS & IMPLEMENTED MITIGATIONS

### 4.1 Insecure Direct Object References (IDOR / BOLA)
- **Scenario**: Attacker logs in as `viewer_carol` and iterates document IDs (`/api/documents/{uuid}`) belonging to `owner_alice`.
- **Mitigation**: All document retrieval, download, metadata modification, and deletion endpoints route strictly through `DocumentAuthorizationService`. Authorization queries the active database permissions on every call. Frontend client-supplied IDs are never trusted.
- **Verification**: Verified in `DocumentSecurityAndAuthorizationTest.testUnauthorizedReadAttemptBlockedAndAudited`.

### 4.2 Unauthorized Document Download Bypass
- **Scenario**: Attacker attempts to download private files by guessing filesystem paths or constructing static direct URLs.
- **Mitigation**: Uploaded files are assigned cryptographically random UUID storage keys (e.g., `8f9b2c1a-...`). The storage directory is located outside the web server root. Downloads are routed through `GET /api/documents/{id}/download`, requiring active READ authorization and streaming binary bytes through Spring Controller responses.
- **Verification**: Verified in `DocumentSecurityAndAuthorizationTest.testReadGrantCannotEditMetadata`.

### 4.3 Session Theft and Cross-Site Request Forgery (CSRF)
- **Scenario**: Adversary tricks authenticated user into executing unwanted state-changing actions via malicious third-party websites.
- **Mitigation**:
  - `CookieCsrfTokenRepository.withHttpOnlyFalse()` issues `XSRF-TOKEN` cookie.
  - State-changing HTTP methods (`POST`, `PUT`, `DELETE`) require `X-XSRF-TOKEN` or `X-CSRF-TOKEN` header.
  - Session cookie (`TRUSTVAULT_SESSION`) is flagged `HttpOnly`, preventing JavaScript access.
  - `SameSite=Lax` restricts cross-site cookie transmission.

### 4.4 Privilege Escalation & Admin Privilege Separation
- **Scenario 1**: Viewer attempts to promote themselves or grant permissions to third parties.
- **Scenario 2**: Admin attempts to view private corporate document contents.
- **Mitigation**:
  - Only the document OWNER can invoke `PermissionService.grantPermission` or `PermissionService.revokePermission`.
  - Administrative roles (`ROLE_ADMIN`) are strictly checked via Spring Security `@PreAuthorize("hasRole('ADMIN')")`.
  - In `DocumentAuthorizationService.authorizeRead`, administrators are NOT granted blanket access to read confidential documents unless explicitly granted by the document owner.
- **Verification**: Verified in `DocumentSecurityAndAuthorizationTest.testViewerCannotGrantPermissions` and `testAdminCannotReadWithoutGrant`.

### 4.5 Malicious File Uploads & Path Traversal
- **Scenario**: Attacker uploads executable payloads (e.g., `.exe`, `.php`, `.sh`) or filenames with directory traversal (`../../../../windows/system32/cmd.exe`).
- **Mitigation**:
  - Extension allowlist: `pdf, docx, doc, txt, csv, xlsx, xls, png, jpg, jpeg, md`.
  - Content inspection via **Apache Tika**: examines magic bytes and rejects files whose content does not match the extension.
  - Filename sanitization: strips path separators across Windows and Unix (`/`, `\`), removes non-alphanumeric characters, and limits filename length.
  - Stored under server-generated UUIDs; original filenames are only used as headers during download.
- **Verification**: Verified in `FileValidationAndStorageTest.testPathTraversalRejected` and `testDangerousExtensionsRejected`.

### 4.6 Audit Trail Tampering
- **Scenario**: Attacker with database access alters the details of a previous `DOCUMENT_DELETE` or `ACCESS_DENIED` event to hide their actions.
- **Mitigation**:
  - Audit trail is constructed as a cryptographic SHA-256 hash chain:
    `current_hash = SHA-256(seq | prev_hash | timestamp_epoch_ms | actor | action | target | outcome | details)`
  - Sequence numbers are strictly monotonic and sequential.
  - Administrator verification endpoint (`POST /api/audit/verify`) traverses the entire chain from genesis to head, recalculates every SHA-256 link, and pinpoints the exact sequence number where tampering occurred.
- **Verification**: Verified in `AuditIntegrityAndTamperTest.testHashChainAndTamperDetection`.

### 4.7 Permission-Revocation Races & Immediate Revocation
- **Scenario**: A user's access is revoked while they are actively browsing; cached permissions allow them to download the file.
- **Mitigation**: TrustVault does not cache document permissions in memory or session state. Permission checks query the database on every protected request. When `revokePermission` deletes the record, the next download request immediately fails with HTTP 403.
- **Verification**: Verified in `DocumentSecurityAndAuthorizationTest.testImmediatePermissionRevocation`.

### 4.8 Account Deactivation Session Invalidation
- **Scenario**: An administrator deactivates a compromised employee account, but the employee's existing browser session continues to make requests.
- **Mitigation**: `DocumentAuthorizationService.checkActiveUser` queries `users.is_active` on every protected request. If `is_active == false`, an `AccountDeactivatedException` is thrown, returning HTTP 403 and terminating access immediately.
- **Verification**: Verified in `DocumentSecurityAndAuthorizationTest.testDeactivatedUserBlockedImmediately`.

### 4.9 Unauthorized Account Creation & Public Registration Abuse
- **Scenario**: Attacker attempts to register arbitrary accounts, assign themselves `ROLE_ADMIN`, or immediately access confidential vaults via self-service signup.
- **Mitigation**:
  - All public self-registrations (`POST /api/auth/register`) default strictly to `ROLE_VIEWER` and `status = PENDING` (`isActive = false`).
  - Spring Security prevents `PENDING` and `REJECTED` users from authenticating or accessing protected APIs.
  - Server-side unique constraints prevent duplicate email or username squatting.
  - Initial administrators can ONLY be provisioned through secure environment variables (`TRUSTVAULT_ADMIN_*`) or trusted bootstrap.
  - Approvals and rejections can only be executed by authenticated administrators via server-side role check (`@PreAuthorize("hasRole('ADMIN')")`), with all actions logged into the cryptographic hash trail (`REGISTRATION_REQUEST`, `REGISTRATION_APPROVE`, `REGISTRATION_REJECT`).
- **Verification**: Verified in `RegistrationAndApprovalWorkflowTest.testRegistrationCreatesPendingAccount`, `testPendingUserCannotLogin`, `testAdminApprovalAllowsLogin`, and `testAdminRejection`.

### 4.10 Segregation of VIEW vs. DOWNLOAD Permissions (Data Exfiltration Prevention)
- **Scenario**: A document owner intends to grant a collaborator read-only visibility into a document's metadata and inspection details, but wishes to prevent the recipient from downloading the raw document payload.
- **Mitigation**:
  - TrustVault implements granular permission levels (`VIEW`, `DOWNLOAD`, `EDIT`).
  - Users with `VIEW`-only access are authorized to inspect document records and metadata (`authorizeRead`), but any binary download invocation (`GET /api/documents/{id}/download`) routes through `authorizeDownload`, throwing `AccessForbiddenException` and recording an `ACCESS_DENIED` security audit event.
  - Frontend controls disable or withhold download actions when `effectivePermission === 'VIEW'`.
- **Verification**: Verified in `DocumentSecurityAndAuthorizationTest.testViewOnlyPermissionCannotDownloadDocument` and `testDownloadPermissionAllowsDownload`.

### 4.11 Time-Bounded Access & Stale Grant Expiration
- **Scenario**: An external consultant is granted temporary access for an audit; after the engagement concludes, forgotten permission grants allow persistent access indefinitely.
- **Mitigation**:
  - Permissions support optional expiration timestamps (`expires_at` column in `document_permissions`).
  - `DocumentAuthorizationService` verifies `expires_at > CURRENT_TIMESTAMP` before granting read, edit, or download access.
  - Expired grants are dynamically filtered out of accessible document queries and immediately trigger `ACCESS_DENIED` audit events upon attempted use.
- **Verification**: Verified in `DocumentSecurityAndAuthorizationTest.testExpiredPermissionRejectedImmediately`.

### 4.12 External HMAC-SHA256 Signed Checkpoints (Database Superuser Tampering Mitigation)
- **Scenario**: An adversary or rogue database administrator compromises database superuser credentials (`postgres`), modifies an incriminating audit record, and regenerates valid SHA-256 hashes for all subsequent records to manufacture a synthetic valid chain.
- **Mitigation**:
  - TrustVault records out-of-band periodic cryptographic checkpoints (every 25 blocks) persisted to an independent append-only log (`./storage/checkpoints/audit_checkpoints.log`).
  - Checkpoint lines combine `seq | hash | timestamp` signed with a dedicated HMAC-SHA256 key (`CHECKPOINT_SECRET`) outside the relational database engine.
  - `AuditService.verifyIntegrity()` verifies both intra-table link continuity and external signed checkpoint hashes, detecting any full-table rewrites or truncation attacks.
- **Verification**: Verified in `AuditIntegrityAndTamperTest.testConcurrentAuditWritesMaintainHashChainIntegrity` and `AuditVerificationEndpointSecurityTest`.

---

## 5. RESIDUAL RISKS

1. **Client-Side Data Retention**: Once a file has been downloaded to an authorized user's local operating system, server-side revocation cannot remotely delete the file from the user's hard drive or prevent offline copying. (Mitigated for view-only users via `VIEW` permission restriction).
2. **Key Material Compromise**: If both database credentials and the server host filesystem (where HMAC checkpoint secrets and private storage keys reside) are simultaneously compromised, an adversary could forge checkpoints. (Recommended: HSM or AWS KMS envelope encryption in production).

---

## 6. PRODUCTION HARDENING RECOMMENDATIONS

1. **WORM (Write-Once-Read-Many) Storage**: Mirror audit trail events to AWS S3 Object Lock in Compliance Mode or an immutable ledger (e.g. AWS QLDB or signed transparency logs).
2. **Database Role Principle of Least Privilege**: Grant the application user `INSERT` and `SELECT` on `audit_events`, but revoke `UPDATE` and `DELETE` at the PostgreSQL grant level (`REVOKE UPDATE, DELETE ON audit_events FROM trustvault_app`).
3. **Multi-Factor Authentication (MFA)**: Integrate TOTP / WebAuthn for owner and admin roles.
4. **Content Security Policy (CSP)**: Configure strict CSP headers (`script-src 'self'`, `object-src 'none'`) to prevent XSS.
5. **Virus Scanning**: Integrate ClamAV daemon into `FileValidationService` to scan incoming multipart streams before writing to storage.
