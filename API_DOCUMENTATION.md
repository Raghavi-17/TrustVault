# TrustVault — REST API Specification & Architecture Guide

**Version:** 1.0.0 (Enterprise Document Security)  
**Security Baseline:** OWASP ASVS 4.0 / OAuth 2.0 / NIST SP 800-63B  
**Authentication Scheme:** Stateful/Stateless Hybrid via HTTP-Only Session Cookie or Bearer JWT with Anti-CSRF Token Validation  

---

## 1. Global Security & Transport Conventions

### 1.1 Headers & Anti-CSRF Protection
- **`Content-Type`**: `application/json` (or `multipart/form-data` for document uploads).
- **`X-XSRF-TOKEN` / `X-CSRF-TOKEN`**: Required on all state-changing HTTP requests (`POST`, `PUT`, `DELETE`). The token value must match the client cookie issued via `GET /api/auth/csrf`.
- **`User-Agent` & IP Tracking**: All security and authorization operations capture client IP (`X-Forwarded-For` or remote address) and `User-Agent` headers for tamper-evident audit trail correlation.

### 1.2 Common API Envelope Format
All JSON responses return a standardized `ApiResponse<T>` envelope:

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... }
}
```

On operational or authorization failure:
```json
{
  "success": false,
  "message": "You do not have permission to download this document",
  "data": null
}
```

### 1.3 Standard HTTP Status Codes
| Status Code | Meaning | Condition |
| :--- | :--- | :--- |
| `200 OK` | Successful execution | Request fulfilled successfully. |
| `201 Created` | Entity created | Resource successfully provisioned (e.g., registration request, category creation). |
| `400 Bad Request` | Input validation failure | Missing required fields, invalid date format, or invalid parameters. |
| `401 Unauthorized` | Unauthenticated | Request missing valid session/JWT or credentials invalid. |
| `403 Forbidden` | Authorization denied | Caller lacks necessary role, document ownership, or explicit permission grant. |
| `404 Not Found` | Entity not found | Target document, user, or category does not exist. |
| `409 Conflict` | Unique constraint violation | Username or email already registered. |
| `429 Too Many Requests` | Rate limit exceeded | Exceeded 5 failed login attempts in 15 minutes. |
| `500 Internal Error` | Server fault | Unhandled runtime exception (stack traces suppressed). |

---

## 2. Authentication & Session Management (`/api/auth`)

### 2.1 CSRF Token Generation
- **Endpoint:** `GET /api/auth/csrf`
- **Access:** Public
- **Description:** Seeds the anti-CSRF token cookie (`XSRF-TOKEN`) for subsequent mutating requests.
- **Response:**
  ```json
  {
    "success": true,
    "message": "CSRF token initialized",
    "data": {
      "token": "d8f1e2c4-...",
      "headerName": "X-XSRF-TOKEN",
      "parameterName": "_csrf"
    }
  }
  ```

### 2.2 Public Account Registration
- **Endpoint:** `POST /api/auth/register`
- **Access:** Public
- **Description:** Submits a self-service registration request. Registrations default to `ROLE_VIEWER` and `status = PENDING`. A designated administrator must approve the account before login is permitted.
- **Request Body:**
  ```json
  {
    "username": "carol_white",
    "email": "carol@example.com",
    "password": "SecurePassword123!",
    "confirmPassword": "SecurePassword123!",
    "fullName": "Carol White"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "success": true,
    "message": "Registration submitted successfully. Your account is pending administrator approval.",
    "data": {
      "id": "u-984a-...",
      "username": "carol_white",
      "email": "carol@example.com",
      "role": "VIEWER",
      "status": "PENDING"
    }
  }
  ```

### 2.3 User Authentication (Login)
- **Endpoint:** `POST /api/auth/login`
- **Access:** Public (Rate-limited to 5 attempts / 15 minutes per IP)
- **Description:** Authenticates via username or email and password. Verifies that the account is `ACTIVE`. Issues HTTP-Only cookie `TRUSTVAULT_SESSION` and returns session metadata.
- **Request Body:**
  ```json
  {
    "username": "owner_alice",
    "password": "Owner@TrustVault2026!"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Login successful",
    "data": {
      "token": "eyJhbGciOi...",
      "user": {
        "id": "u-123",
        "username": "owner_alice",
        "email": "alice@trustvault.internal",
        "fullName": "Alice Smith",
        "role": "OWNER",
        "status": "ACTIVE"
      }
    }
  }
  ```

### 2.4 Current User Inspection
- **Endpoint:** `GET /api/auth/me`
- **Access:** Authenticated
- **Description:** Returns the authenticated user's profile and active permissions.

### 2.5 User Logout
- **Endpoint:** `POST /api/auth/logout`
- **Access:** Authenticated
- **Description:** Invalidates the server-side session and clears client security cookies.

---

## 3. Document Management (`/api/documents`)

### 3.1 Upload Document
- **Endpoint:** `POST /api/documents`
- **Access:** Authenticated (`ROLE_OWNER` or `ROLE_ADMIN`)
- **Content-Type:** `multipart/form-data`
- **Form Parameters:**
  - `file`: Binary file stream (max 15 MB; extensions: `pdf, docx, doc, txt, csv, xlsx, xls, png, jpg, jpeg, md`).
  - `title`: String (required).
  - `description`: String (optional).
  - `categoryId`: String UUID (optional).
  - `tags`: String comma-separated (optional).
- **Validation:** Inspects magic bytes via Apache Tika. Rejects MIME-extension mismatches and path-traversal filenames. Calculates deterministic SHA-256 content checksum.

### 3.2 List Accessible Documents
- **Endpoint:** `GET /api/documents`
- **Access:** Authenticated
- **Query Parameters:** `page` (int), `size` (int), `title` (string), `categoryId` (string), `ownerId` (string), `fromDate` (ISO-8601), `toDate` (ISO-8601).
- **Authorization Logic:** Returns documents where the caller is either the `owner` OR has an active, non-expired entry in `document_permissions`.

### 3.3 Get Document Details
- **Endpoint:** `GET /api/documents/{id}`
- **Access:** Authenticated
- **Authorization:** Caller must be Document OWNER or hold active `VIEW`, `DOWNLOAD`, `READ`, or `EDIT` permission.
- **Response Data:** Includes `effectivePermission` (`"OWNER"`, `"EDIT"`, `"DOWNLOAD"`, `"READ"`, or `"VIEW"`).

### 3.4 Download Document Binary Content
- **Endpoint:** `GET /api/documents/{id}/download`
- **Access:** Authenticated
- **Authorization:** Caller must be Document OWNER or hold `DOWNLOAD`, `READ` (legacy), or `EDIT` permission.
- **Restriction:** Users with `VIEW`-only permission are rejected with HTTP 403 (`ACCESS_DENIED` logged to audit trail).
- **Response:** Binary octet-stream with sanitized `Content-Disposition: attachment; filename="..."`.

### 3.5 Update Document Metadata
- **Endpoint:** `PUT /api/documents/{id}`
- **Access:** Authenticated (OWNER or active `EDIT` permission).

### 3.6 Delete Document
- **Endpoint:** `DELETE /api/documents/{id}`
- **Access:** Authenticated (**Document OWNER ONLY**).
- **Description:** Permanently deletes the private storage object and all associated permissions, recording `DOCUMENT_DELETE` in the audit chain.

---

## 4. Permission & Sharing Management (`/api/documents/{id}/permissions`)

### Permission Hierarchy Matrix
| Permission Level | View Metadata / Details | Download Binary Content | Update Metadata / Revision | Manage Permissions |
| :--- | :---: | :---: | :---: | :---: |
| **OWNER** | Yes | Yes | Yes | Yes |
| **EDIT** | Yes | Yes | Yes | No |
| **DOWNLOAD** | Yes | Yes | No | No |
| **READ** (Legacy) | Yes | Yes | No | No |
| **VIEW** | Yes | **No** | No | No |

### 4.1 Grant Permission
- **Endpoint:** `POST /api/documents/{id}/permissions`
- **Access:** Authenticated (**Document OWNER ONLY**)
- **Request Body:**
  ```json
  {
    "userId": "u-viewer-carol",
    "permission": "VIEW",
    "expiresAt": "2026-12-31T23:59:59Z"
  }
  ```
- **Notes:** `expiresAt` is optional. If provided, grants are automatically invalidated once the timestamp expires.

### 4.2 Modify Permission
- **Endpoint:** `PUT /api/documents/{id}/permissions/{userId}`
- **Access:** Authenticated (**Document OWNER ONLY**)
- **Request Body:**
  ```json
  {
    "permission": "DOWNLOAD",
    "expiresAt": "2026-11-30T18:00:00Z"
  }
  ```

### 4.3 Revoke Permission Immediately
- **Endpoint:** `DELETE /api/documents/{id}/permissions/{userId}`
- **Access:** Authenticated (**Document OWNER ONLY**)
- **Enforcement:** Takes effect instantaneously on the database record; no cached session permissions.

---

## 5. Tamper-Evident Audit Trail (`/api/audit`)

### 5.1 Query Audit History
- **Endpoint:** `GET /api/audit/events`
- **Access:** Authenticated (`ROLE_ADMIN` only)
- **Query Parameters:**
  - `user`: Filter by username or actor UUID.
  - `action`: Filter by `AuditEventType` (e.g., `DOCUMENT_UPLOAD`, `DOCUMENT_DOWNLOAD`, `ACCESS_DENIED`, etc.).
  - `document`: Filter by target document ID or description keyword.
  - `startDate`: ISO-8601 start timestamp.
  - `endDate`: ISO-8601 end timestamp.
  - `outcome`: Filter by `SUCCESS`, `DENIED`, or `FAILED`.
  - `page`: Page index (default 0).
  - `size`: Page size (default 20).

### 5.2 Read-Only Hash Chain Verification
- **Endpoint:** `GET /api/audit/verify`
- **Access:** Authenticated (Any active authenticated user)
- **Description:** Non-mutating read-only traversal verifying SHA-256 hash chains from genesis to head, and secondary HMAC-SHA256 checkpoints.

### 5.3 Administrative Integrity Verification
- **Endpoint:** `POST /api/audit/verify`
- **Access:** Authenticated (`ROLE_ADMIN` only)
- **Description:** Executes cryptographic integrity verification and records an `AUDIT_INTEGRITY_VERIFY` event in the audit trail.
- **Response:**
  ```json
  {
    "success": true,
    "message": "Audit integrity verification completed",
    "data": {
      "status": "CHAIN_INTACT",
      "verificationStatus": "VERIFIED",
      "isChainValid": true,
      "totalEventsChecked": 42,
      "integrityErrorsDetected": 0,
      "brokenSequenceNumber": null,
      "affectedEventIds": [],
      "lastVerificationTimestamp": "2026-10-25T10:45:00Z",
      "lastSuccessfulVerification": "2026-10-25T10:45:00Z",
      "message": "Audit trail cryptographic integrity verified. All 42 blocks valid."
    }
  }
  ```

---

## 6. Administration & Governance (`/api/admin`)

- `GET /api/admin/dashboard`: System stats (active users, total documents, downloads, audit entries).
- `GET /api/admin/registrations/pending`: Pending self-registration requests awaiting approval.
- `POST /api/admin/registrations/{id}/approve`: Approves pending user and activates account.
- `POST /api/admin/registrations/{id}/reject`: Rejects registration and prevents authentication.
- `GET /api/admin/users`: Comprehensive user management table.
- `PUT /api/admin/users/{id}/status`: Activates or deactivates user accounts.
- `POST /api/admin/categories`: Provisions organizational document categories (audited via `CATEGORY_CREATE`).
