# TRUSTVAULT AI ASSISTANT DEVELOPMENT LOG

This file documents genuine AI interactions, architectural decisions, authorization designs, audit integrity verifications, failures encountered, and fixes applied throughout the construction of TrustVault.

---

### Entry 1 — 2026-10-22: Workspace Inspection and Technology Stack Bootstrap
- **Prompt Used:**
  "Inspect my currently opened TrustVault workspace and environment first. Then build the complete frontend, backend, database, authorization system, private file storage, audit trail, and administrative features directly in the project. Use the Sage Green + Cream color system and implement the working light/dark mode toggle as part of the actual application."
- **Problem Addressed:**
  Bootstrapping the TrustVault repository from an empty directory, inspecting local toolchains (Java 17, Node v24, npm), installing Maven 3.9.9 portably, and architecting the full-stack project structure across `backend`, `frontend`, `database`, and `docs`.
- **Implementation & Files:**
  - Configured Apache Maven 3.9.9 in `C:\Users\ragha\.maven\apache-maven-3.9.9`.
  - Created root project structure (`frontend`, `backend`, `database`, `docs`).
  - Added `.gitignore`, `.env.example`, `docker-compose.yml`.
- **Result:**
  Environment verified: Java 17 (Microsoft Build 17.0.20.1), Node v24.19.0, npm 11.17.0, and Maven 3.9.9 operational.
- **Verification:**
  Executed `java -version`, `mvn -v`, `node -v`, `npm -v` via PowerShell.

---

### Entry 2 — 2026-10-22: Java IPv6 Maven Resolution & Cached Spring Boot Alignment
- **Prompt Used:**
  "Build the complete backend, database, authorization system, private file storage, audit trail, and administrative features directly in the project."
- **Problem Addressed:**
  When executing `mvn compile`, the build failed with `Connect to repo.maven.apache.org:443 [2606:4700:9ae3:fa77:15fe:1069:c262:b56] failed: Connect timed out`. Java attempted IPv6 connections over an interface lacking external IPv6 routing.
- **Implementation & Files:**
  - Diagnosed connectivity with `curl.exe` (HTTP 200 via IPv4).
  - Configured `-Djava.net.preferIPv4Stack=true` in `MAVEN_OPTS`.
  - Updated `backend/pom.xml` to match Spring Boot 3.2.4 (present in user's local `.m2` repository).
- **Result:**
  Maven resolved parent POM and dependencies smoothly.
- **Verification:**
  Subsequent compilation passed dependency resolution.

---

### Entry 3 — 2026-10-22: File Validation MIME Mapping & Compilation Success
- **Prompt Used:**
  "Implement secure file validation with extension allowlist and MIME type verification."
- **Problem Addressed:**
  `javac` reported compilation error in `FileValidationService.java`: `no suitable method found for Map.of(...)` due to 11 key-value pairs exceeding `Map.of()` 10-entry method limit.
- **Implementation & Files:**
  - Modified `backend/src/main/java/com/trustvault/storage/FileValidationService.java` to use `Map.ofEntries(Map.entry(...))`.
- **Result:**
  Backend successfully compiled all 62 source files (`BUILD SUCCESS`).
- **Verification:**
  Executed `& "C:\Users\ragha\.maven\apache-maven-3.9.9\bin\mvn.cmd" compile -DskipTests` -> 0 errors.

---

### Entry 4 — 2026-10-22: Audit Logging Isolation & Propagation Fix
- **Prompt Used:**
  "Verify that unauthorized access attempts and immediate revocations are audited correctly without rollback loss."
- **Problem Addressed:**
  When executing `DocumentSecurityAndAuthorizationTest`, test `testUnauthorizedReadAttemptBlockedAndAudited` failed with `expected: <1> but was: <0>` ACCESS_DENIED audit events. Because `authorizeRead` was annotated with `@Transactional(readOnly = true)` and threw `AccessForbiddenException`, the transaction rollback discarded the recorded audit event. Security audit logs must NEVER be rolled back when a security violation occurs.
- **Implementation & Files:**
  - Modified `backend/src/main/java/com/trustvault/audit/AuditService.java`.
  - Configured `@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)` on `recordEvent`.
- **Result:**
  Audit records are committed in an autonomous transaction boundary immediately upon generation. Even when the invoking service throws `AccessForbiddenException` or `AccountDeactivatedException`, the security trail is immutably persisted.
- **Verification:**
  Re-ran `DocumentSecurityAndAuthorizationTest`: all 8 security test cases passed.

---

### Entry 5 — 2026-10-22: Cryptographic Hash Chain Timestamp Parity & Tamper Detection
- **Prompt Used:**
  "Ensure the cryptographic SHA-256 audit hash chain deterministic serialization is completely resilient across database engines and timezones."
- **Problem Addressed:**
  `AuditIntegrityAndTamperTest` reported hash mismatch during chain verification. Database timestamp columns without time zone truncate nanoseconds to milliseconds or microseconds and shift according to local JVM time zones (e.g. `Asia/Kolkata` vs `UTC`). When reading back `event.getTimestamp()`, the recomputed SHA-256 hash differed from the original insert-time hash.
- **Implementation & Files:**
  - Added `timestamp_epoch_ms` (`BIGINT`) column to `V1__init_schema.sql` and `AuditEvent.java`.
  - Updated `AuditService.computeHash` to serialize timestamps deterministically using `timestamp.toEpochMilli()` (`Long`), which is integer-exact and immune to string formatting or database timezone quirks.
  - Updated `DataInitializer.java` and `AuditService.recordEvent` to set and verify `timestampEpochMs`.
- **Result:**
  Deterministic SHA-256 recalculation passed with 100% precision. The tamper detection test successfully detected when row details were modified, pinpointing the exact sequence number.
- **Verification:**
  Executed `& "C:\Users\ragha\.maven\apache-maven-3.9.9\bin\mvn.cmd" test`: 19/19 backend tests passed.

### Entry 6 — 2026-10-22: User Registration & Admin Approval Workflow Implementation
- **Prompt Used:**
  "Update Existing TrustVault Project — Registration with Admin Approval. Create a complete, functional registration page. New registrations must require administrator approval before users can log in. Pending users must not be allowed to log in or access protected APIs. The admin dashboard must contain a Pending Registrations section with Approve and Reject actions. Keep the existing Login page, remove sample usernames/passwords, and add a link to Register. Record all security events in the tamper-evident audit trail."
- **Problem Addressed:**
  - Allowing public users to request vault accounts safely without self-assigning administrative rights or gaining immediate access to confidential corporate files.
  - Enforcing strict status lifecycle (`PENDING` -> `ACTIVE` or `REJECTED`) with database constraints and server-side state machines.
  - Removing exposed sample account credentials from the Login page to satisfy enterprise security standards.
- **Implementation & Files:**
  - Created `UserStatus` enum (`PENDING`, `ACTIVE`, `REJECTED`, `DEACTIVATED`) and added `status` column to `User.java` and `UserDto.java`.
  - Added Flyway migration `V2__add_user_status.sql` in `backend/src/main/resources/db/migration` and `database/migrations`.
  - Implemented `RegisterRequest.java` DTO and `POST /api/auth/register` in `AuthController.java` and `UserService.java`. Public registrations always default to `Role.VIEWER` and `UserStatus.PENDING`.
  - Updated `CustomUserDetails.java` and `CustomUserDetailsService.java` to support dual login via username or email address, checking `isEnabled() = (status == UserStatus.ACTIVE)`.
  - Updated `AuthController.login` to intercept disabled attempts, returning clear status messages ("Your registration request is pending administrator approval.") and cryptographically logging audit entries.
  - Added `GET /api/admin/registrations/pending`, `POST /api/admin/registrations/{id}/approve`, and `POST /api/admin/registrations/{id}/reject` in `AdminController.java`.
  - Added `REGISTRATION_REQUEST`, `REGISTRATION_APPROVE`, and `REGISTRATION_REJECT` to `AuditEventType.java`.
  - Configured initial administrator bootstrap via environment variables (`TRUSTVAULT_ADMIN_*`) in `DataInitializer.java`.
  - Created `frontend/src/pages/RegisterPage.jsx` with real backend submission, input validation, and pending approval guidance.
  - Updated `frontend/src/pages/LoginPage.jsx` to remove the hardcoded demo credentials box and add a direct link to the Registration page.
  - Updated `frontend/src/pages/AdminPage.jsx` to add the **Pending Registrations** management table with role assignment and one-click Approve/Reject actions.
  - Added `RegistrationAndApprovalWorkflowTest.java` covering public registration, duplicate email/username rejection, password mismatch, pending login rejection, admin approval enabling login, and admin rejection.
- **Result:**
  - Backend test suite expanded to 28 automated integration tests: 100% passing (`BUILD SUCCESS`).
  - Frontend built cleanly (`npm run build` completed with 0 errors).
  - All theme and design system tests passing.
- **Verification:**
  - Ran `$env:MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"; mvn test` -> 28/28 tests passed.
  - Ran `npm run build` and `node test/theme.test.js` -> 0 errors.

---


