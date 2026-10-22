# TrustVault — Secure Document Sharing with a Tamper-Evident Audit Trail

TrustVault is a full-stack, enterprise-grade document sharing web application engineered with strict server-side authorization, private isolated file storage, and a cryptographically verifiable SHA-256 audit trail.

---

## 1. Core Capabilities & Architecture

- **User Self-Registration & Administrator Approval Workflow**: Public registration allows new users to request access (`fullName`, `email`, `password`, `confirmPassword`). Accounts are created in `PENDING` status with restricted `VIEWER` role. Pending and rejected users cannot authenticate or access protected APIs.
- **Administrator Review Dashboard**: Administrators review incoming registration requests in the dedicated **Pending Registrations** panel to either **Approve** (with optional role assignment: `VIEWER` or `OWNER`) or **Reject**. All status decisions are cryptographically recorded in the SHA-256 audit trail.
- **Secure Administrator Setup**: Public registration can never create an administrator account. The initial administrator is bootstrapped via environment variables (`TRUSTVAULT_ADMIN_*`) with BCrypt password hashing.
- **Fine-Grained Document Authorization**: Central server-side policy service enforcing per-document permission levels (`OWNER`, `READ`, `EDIT`) and user roles (`ADMIN`, `OWNER`, `VIEWER`). Hidden frontend buttons are never treated as security controls.
- **Immediate Grant Revocation**: Deleting a permission record immediately terminates read/download access on the very next request with no caching delays or lingering presigned URLs.
- **Tamper-Evident Audit Trail**: Every sensitive operation (registration, approval, rejection, login, upload, download, permission grant/revoke, metadata edit, content replacement, access denial) is recorded into an append-oriented cryptographic SHA-256 hash chain:
  $$\text{Current Hash} = \text{SHA-256}(\text{Seq} \parallel \text{PrevHash} \parallel \text{EpochMs} \parallel \text{Actor} \parallel \text{Action} \parallel \text{Target} \parallel \text{Outcome} \parallel \text{Details})$$
- **On-Demand Cryptographic Integrity Verification**: Administrators can verify the entire hash chain from genesis to head at any time, pinpointing any modified block or sequence gap.
- **Strict Secure File Validation**: All uploads are inspected with **Apache Tika** magic-byte analysis, cross-checked against an extension allowlist, scanned for path traversal patterns, and stored outside the web root under server-generated UUID keys.
- **Enterprise Sage Green + Cream Design System**: Complete, accessible light and dark themes using forest charcoal and muted sage (no navy blue), with an instant toggle persisted across sessions via `localStorage`.
- **Privilege Separation**: Administrators cannot automatically read confidential documents merely because they hold an admin role.

---

## 2. Technology Stack

### Backend
- **Java 17** (Microsoft OpenJDK 17 LTS)
- **Spring Boot 3.2.4** (with Spring Security, Spring Data JPA, Bean Validation, Actuator)
- **Apache Tika 2.9.2** (magic byte inspection & MIME validation)
- **Springdoc OpenAPI 2.5.0** (OpenAPI / Swagger documentation)
- **Flyway Database Migrations** (V1 initial schema, V2 user status)
- **JUnit 5 & MockMvc**

### Frontend
- **React 18** + **Vite 5**
- **React Router 6**
- **Lucide React** (SVG icons)
- **Centralized CSS Variables Design System** (Light: `#F7F7F0` Cream + `#4CAF7D` Sage Green; Dark: `#18231E` Forest Charcoal + `#83C99C` Muted Sage)

### Database & Storage
- **PostgreSQL 16/17** (with Flyway schema versioning)
- **Private Local Filesystem Storage** (default local storage outside web root)
- **S3-Compatible Object Storage Abstraction** (ready for AWS S3 or MinIO)

---

## 3. User Accounts & Demo Capabilities

For local evaluation, demonstrations, and hackathon presentation, the Login screen provides convenient **Demo Accounts quick-fill buttons** (`Admin`, `Owner (Alice)`, `Owner (Bob)`, `Viewer (Carol)`) alongside the **Request Account Registration** link. 

Each demo account authenticates through real backend security, establishes an authenticated HTTP-only session, and respects full per-document permissions without bypassing authorization:

| Role | Username / Identifier | Password | Status | Purpose & Capabilities |
| :--- | :--- | :--- | :--- | :--- |
| **ADMIN** | `admin` (or `admin@trustvault.local`) | `Admin@TrustVault2026!` | `ACTIVE` | Review pending registrations (Approve/Reject), manage user accounts, inspect audit logs, verify cryptographic integrity. Cannot view confidential documents without explicit owner grant. |
| **OWNER** | `owner_alice` (or `alice@trustvault.local`) | `Owner@TrustVault2026!` | `ACTIVE` | Upload documents, download own files, edit metadata, replace file content, grant/revoke READ/EDIT permissions, delete documents. |
| **OWNER** | `owner_bob` (or `bob@trustvault.local`) | `OwnerBob@TrustVault2026!` | `ACTIVE` | Secondary document owner for cross-user permission testing. |
| **VIEWER** | `viewer_carol` (or `carol@trustvault.local`) | `Viewer@TrustVault2026!` | `ACTIVE` | Read and download documents explicitly shared with her. Blocked from unshared documents. |

*Quick-fill buttons are provided directly on the Login screen for seamless evaluation.*

Initial administrator credentials can also be configured at startup via environment variables or Spring properties:
```bash
# Optional Environment Variables for Administrator Bootstrap
TRUSTVAULT_ADMIN_USERNAME=admin
TRUSTVAULT_ADMIN_PASSWORD=Admin@TrustVault2026!
TRUSTVAULT_ADMIN_EMAIL=admin@trustvault.local
TRUSTVAULT_ADMIN_FULLNAME="System Administrator"
```

---

## 4. Quick Start & Local Execution

### Prerequisites
- Java 17+
- Node.js 18+ (tested on Node v24)
- npm 9+
- Docker (optional, for PostgreSQL container)

### Step 1: Start PostgreSQL (Optional Docker Compose)
```bash
docker compose up -d
```
*Note: If PostgreSQL is not running locally, TrustVault automatically boots with its zero-configuration local profile (`application-local.yml`).*

### Step 2: Build & Start the Backend
```powershell
cd backend
$env:MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"
mvn spring-boot:run
```
*The backend starts at `http://localhost:8080`. Interactive Swagger UI is available at `http://localhost:8080/swagger-ui.html`.*

### Step 3: Start the Frontend
```powershell
cd frontend
npm install
npm run dev
```
*The frontend starts at `http://localhost:5173`. Open your browser to explore TrustVault.*

---

## 5. Automated Test Execution

### Run Backend Tests (28 Automated Tests)
```powershell
cd backend
$env:MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"
mvn test
```
*Test suite validates:*
- Public registration creates `PENDING` accounts with `ROLE_VIEWER`
- Pending user login rejection (`403 Forbidden`) with informative message
- Rejected user login rejection (`403 Forbidden`) with informative message
- Administrator approval transitions account to `ACTIVE` and enables login
- Administrator rejection transitions account to `REJECTED`
- Duplicate username and email prevention with server-side validation
- Password matching validation on registration
- Non-admin blocked from administrative endpoints (`403 Forbidden`)
- Unauthenticated access rejection (`401 Unauthorized`)
- IDOR / BOLA protection & cross-user isolation
- Unauthorized document reading, editing, and deletion blocking
- Immediate access revocation taking effect on next request
- Deactivated user immediate session termination
- Malicious upload rejection (path traversal, `.exe`, `.php`, empty files)
- Audit trail SHA-256 hash chaining and live tamper detection

### Run Frontend Design & Theme Tests (3 Automated Tests)
```powershell
cd frontend
npm test
```
*Validates Sage Green + Cream color tokens, localStorage theme preference persistence, and accessible toggle operation.*

---

## 6. Project Structure

```
TrustVault/
├── backend/
│   ├── src/main/java/com/trustvault/
│   │   ├── admin/           # Administrative metrics & oversight service
│   │   ├── audit/           # Cryptographic SHA-256 hash-chain audit trail
│   │   ├── auth/            # Session authentication, UserDetails, Rate limiting
│   │   ├── authorization/   # Central DocumentAuthorizationService (BOLA/IDOR)
│   │   ├── common/          # GlobalExceptionHandler, ApiResponse, Exceptions
│   │   ├── config/          # Spring Security, CORS, CSRF, OpenAPI, DataInitializer
│   │   ├── document/        # Document & Category entities, DTOs, controllers
│   │   ├── permission/      # Document sharing, permission grants & revocation
│   │   ├── storage/         # LocalStorageService, Apache Tika validation
│   │   └── user/            # User entity, roles, repository, service
│   ├── src/main/resources/
│   │   ├── db/migration/    # Flyway database migrations (V1 schema, V2 seed)
│   │   └── application.yml  # Multi-profile Spring configuration
│   └── src/test/java/       # Security, authorization, and tamper test suite
├── frontend/
│   ├── src/
│   │   ├── api/             # Reusable API client with session & CSRF handling
│   │   ├── components/      # Navbar, Sidebar, ThemeToggle, Modal, ConfirmDialog
│   │   ├── context/         # AuthContext & ThemeContext
│   │   ├── pages/           # Login, Dashboard, Documents, Detail, Upload, Admin, Audit
│   │   └── index.css        # Sage Green + Cream / Forest Charcoal theme system
│   └── test/                # Frontend theme and design system tests
├── database/migrations/     # Standalone SQL migrations
├── docs/
│   └── API.md               # Complete REST API specification
├── THREATS.md               # Formal threat model and security analysis
├── PROMPTS.md               # Genuine AI assistant development log
├── docker-compose.yml       # Local PostgreSQL container service
└── README.md                # Project documentation
```

---

## 7. Known Limitations & Production Hardening

1. **Client-Side Data Retention**: Server-side permission revocation cannot remotely delete a file that an authorized user already downloaded to their physical device.
2. **Whole-Chain Database Rewrite**: While a SHA-256 hash chain reliably detects tampering of individual blocks, an attacker with full database superuser privileges could theoretically regenerate the chain. Production hardening includes streaming audit records to AWS S3 Object Lock (WORM compliance mode) or external signed ledgers.
