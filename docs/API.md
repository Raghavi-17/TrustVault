# TRUSTVAULT REST API DOCUMENTATION

This document provides complete technical specifications for the TrustVault RESTful API endpoints.

**Interactive OpenAPI / Swagger Documentation**: Available at `/swagger-ui.html` and `/api/v3/api-docs` when running the backend.

---

## 1. AUTHENTICATION & SESSION ENDPOINTS

### 1.1 Login & Session Establishment
- **Method**: `POST`
- **Path**: `/api/auth/login`
- **Authentication**: None (Public)
- **Rate Limiting**: 5 failed attempts per 15-minute sliding window per IP/username.
- **Request Body**:
  ```json
  {
    "username": "owner_alice",
    "password": "Owner@TrustVault2026!"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "message": "Login successful",
    "data": {
      "id": "e45a2781-...",
      "username": "owner_alice",
      "fullName": "Alice Jenkins (Owner)",
      "email": "alice@trustvault.local",
      "role": "OWNER",
      "active": true
    },
    "timestamp": "2026-10-22T09:20:00Z"
  }
  ```
- **Error Responses**: `401 Unauthorized` (Invalid credentials), `403 Forbidden` (Account deactivated), `429 Too Many Requests` (Rate limit exceeded).
- **Audit Behavior**: Logs `LOGIN_SUCCESS` on success or `LOGIN_FAILURE` on denial.

### 1.2 Session Termination (Logout)
- **Method**: `POST`
- **Path**: `/api/auth/logout`
- **Authentication**: Session Cookie (`TRUSTVAULT_SESSION`)
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "message": "Logged out successfully",
    "data": null,
    "timestamp": "2026-10-22T09:21:00Z"
  }
  ```
- **Audit Behavior**: Logs `LOGOUT`.

### 1.3 Current User Profile
- **Method**: `GET`
- **Path**: `/api/auth/me`
- **Authentication**: Session Cookie
- **Response** (`200 OK`): Returns currently authenticated user details. Re-verifies active status in database.

### 1.4 CSRF Token Acquisition
- **Method**: `GET`
- **Path**: `/api/auth/csrf`
- **Authentication**: None (Public)
- **Response** (`200 OK`): Returns CSRF token and sets `XSRF-TOKEN` cookie.

---

## 2. DOCUMENT MANAGEMENT ENDPOINTS

### 2.1 Upload Document
- **Method**: `POST`
- **Path**: `/api/documents`
- **Content-Type**: `multipart/form-data`
- **Authentication**: Required (`ROLE_OWNER` or `ROLE_ADMIN`)
- **Parameters**:
  - `file`: Binary file part (Max 15MB, allowlist extensions).
  - `title`: String (Required, max 255 chars).
  - `description`: String (Optional).
  - `categoryId`: String UUID (Optional).
  - `tags`: String (Optional, comma-separated).
- **Response** (`201 Created`):
  ```json
  {
    "success": true,
    "message": "Document uploaded successfully",
    "data": {
      "id": "b18a29e4-...",
      "title": "Q3 2026 Financial Projections",
      "description": "Quarterly audit statement",
      "category": { "id": "cat-1", "name": "Finance", "description": "..." },
      "originalFilename": "projections.xlsx",
      "mimeType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "fileSizeBytes": 45892,
      "sha256Hash": "3f8a4c9b...",
      "version": 1,
      "tags": "finance, confidential",
      "ownerId": "usr-1",
      "ownerUsername": "owner_alice",
      "effectivePermission": "OWNER",
      "createdAt": "2026-10-22T09:25:00Z",
      "updatedAt": "2026-10-22T09:25:00Z"
    }
  }
  ```
- **Audit Behavior**: Logs `DOCUMENT_UPLOAD` with sequence number and SHA-256 hash chaining.

### 2.2 List Accessible Documents
- **Method**: `GET`
- **Path**: `/api/documents`
- **Authentication**: Required
- **Query Parameters**:
  - `page`: Integer (default 0)
  - `size`: Integer (default 10)
  - `sortBy`: String (`createdAt`, `title`, `fileSizeBytes`)
  - `direction`: String (`asc`, `desc`)
- **Response** (`200 OK`): Paginated list of documents where user is OWNER or holds active READ/EDIT permission.

### 2.3 Search Accessible Documents
- **Method**: `GET`
- **Path**: `/api/documents/search`
- **Authentication**: Required
- **Query Parameters**: `query`, `categoryId`, `ownerId`, `fromDate`, `toDate`, `page`, `size`, `sortBy`, `direction`.
- **Response** (`200 OK`): Filtered list restricted to accessible documents.

### 2.4 Document Details
- **Method**: `GET`
- **Path**: `/api/documents/{id}`
- **Authentication**: Required (OWNER or READ/EDIT grant)
- **Response** (`200 OK`): Complete document metadata and SHA-256 hash.
- **Audit Behavior**: Logs `DOCUMENT_READ`.

### 2.5 Secure Binary Download
- **Method**: `GET`
- **Path**: `/api/documents/{id}/download`
- **Authentication**: Required (OWNER or READ/EDIT grant)
- **Response** (`200 OK`): Binary stream with `Content-Disposition: attachment; filename="..."`.
- **Audit Behavior**: Logs `DOCUMENT_DOWNLOAD`.

### 2.6 Update Metadata
- **Method**: `PUT`
- **Path**: `/api/documents/{id}`
- **Authentication**: Required (OWNER or EDIT grant)
- **Request Body**:
  ```json
  {
    "title": "Updated Title",
    "description": "Updated description",
    "categoryId": "cat-uuid",
    "tags": "revised, legal"
  }
  ```
- **Response** (`200 OK`): Updated document DTO.
- **Audit Behavior**: Logs `METADATA_EDIT`.

### 2.7 Replace File Content
- **Method**: `POST`
- **Path**: `/api/documents/{id}/content`
- **Content-Type**: `multipart/form-data`
- **Authentication**: Required (OWNER or EDIT grant)
- **Parameters**: `file` (Multipart file)
- **Response** (`200 OK`): Increments version to `v + 1`, calculates new SHA-256, purges old file.
- **Audit Behavior**: Logs `CONTENT_REPLACE`.

### 2.8 Delete Document
- **Method**: `DELETE`
- **Path**: `/api/documents/{id}`
- **Authentication**: Required (OWNER ONLY)
- **Response** (`200 OK`): Deletes file from private storage, cascades delete on permissions.
- **Audit Behavior**: Logs `DOCUMENT_DELETE`.

---

## 3. PERMISSION & SHARING ENDPOINTS

### 3.1 List Document Permissions
- **Method**: `GET`
- **Path**: `/api/documents/{documentId}/permissions`
- **Authentication**: Required (OWNER ONLY)
- **Response** (`200 OK`): List of active grants.

### 3.2 Grant Permission
- **Method**: `POST`
- **Path**: `/api/documents/{documentId}/permissions`
- **Authentication**: Required (OWNER ONLY)
- **Request Body**:
  ```json
  {
    "userId": "usr-uuid",
    "permission": "READ"
  }
  ```
- **Response** (`201 Created`): Permission DTO.
- **Audit Behavior**: Logs `PERMISSION_GRANT`.

### 3.3 Update Permission Level
- **Method**: `PUT`
- **Path**: `/api/documents/{documentId}/permissions/{targetUserId}`
- **Authentication**: Required (OWNER ONLY)
- **Request Body**: `{ "permission": "EDIT" }`
- **Response** (`200 OK`): Updated permission DTO.
- **Audit Behavior**: Logs `PERMISSION_MODIFY`.

### 3.4 Revoke Permission Immediately
- **Method**: `DELETE`
- **Path**: `/api/documents/{documentId}/permissions/{targetUserId}`
- **Authentication**: Required (OWNER ONLY)
- **Response** (`200 OK`): Access revoked immediately.
- **Audit Behavior**: Logs `PERMISSION_REVOKE`. Next request by target user fails immediately with `403 Forbidden`.

---

## 4. TAMPER-EVIDENT AUDIT TRAIL ENDPOINTS

### 4.1 Query Audit Trail
- **Method**: `GET`
- **Path**: `/api/audit/events`
- **Authentication**: Required (`ROLE_ADMIN`)
- **Query Parameters**: `outcome`, `action`, `page`, `size`.
- **Response** (`200 OK`): Paginated list of audit events with sequence numbers, timestamps, and SHA-256 block hashes.

### 4.2 Verify Cryptographic Integrity
- **Method**: `POST`
- **Path**: `/api/audit/verify`
- **Authentication**: Required (`ROLE_ADMIN`)
- **Response** (`200 OK`):
  ```json
  {
    "success": true,
    "message": "Audit integrity verification completed",
    "data": {
      "isChainValid": true,
      "totalEventsChecked": 42,
      "brokenSequenceNumber": null,
      "message": "Audit trail cryptographic integrity verified. All 42 blocks valid.",
      "verifiedAt": "2026-10-22T09:30:00Z"
    }
  }
  ```
- **Audit Behavior**: Logs `AUDIT_INTEGRITY_VERIFY`.

---

## 5. ADMINISTRATIVE & HEALTH ENDPOINTS

### 5.1 Admin Dashboard Metrics
- **Method**: `GET`
- **Path**: `/api/admin/dashboard`
- **Authentication**: Required (`ROLE_ADMIN`)
- **Response** (`200 OK`): Real aggregated security statistics, account counts, access denial numbers, and verification status.

### 5.2 User Account Status Toggle
- **Method**: `PUT`
- **Path**: `/api/users/{id}/status`
- **Authentication**: Required (`ROLE_ADMIN`)
- **Request Body**: `{ "isActive": false }`
- **Response** (`200 OK`): Updated user record.
- **Audit Behavior**: Logs `ACCOUNT_DEACTIVATE` or `ACCOUNT_ACTIVATE`.

### 5.3 Health Check
- **Method**: `GET`
- **Path**: `/api/health`
- **Authentication**: None (Public)
- **Response** (`200 OK`): `{ "status": "UP", "application": "TrustVault", "version": "1.0.0" }`.
