package com.trustvault.document;

import com.trustvault.auth.SecurityUtils;
import com.trustvault.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Documents", description = "Document management, upload, download, and search")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Upload a private document with metadata")
    public ResponseEntity<ApiResponse<DocumentDto>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "tags", required = false) String tags,
            HttpServletRequest request) {

        String actorId = SecurityUtils.getCurrentUserId();
        String actorUsername = SecurityUtils.getCurrentUsername();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        DocumentDto uploaded = documentService.uploadDocument(
                title,
                description,
                categoryId,
                tags,
                file,
                actorId,
                actorUsername,
                clientIp,
                userAgent
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Document uploaded successfully", uploaded));
    }

    @GetMapping("/documents")
    @Operation(summary = "List documents accessible to current user (owner or shared)")
    public ResponseEntity<ApiResponse<Page<DocumentDto>>> getAccessibleDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        String userId = SecurityUtils.getCurrentUserId();
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<DocumentDto> documents = documentService.getAccessibleDocuments(userId, pageRequest);
        return ResponseEntity.ok(ApiResponse.ok(documents));
    }

    @GetMapping("/documents/search")
    @Operation(summary = "Search and filter accessible documents")
    public ResponseEntity<ApiResponse<Page<DocumentDto>>> searchDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        String userId = SecurityUtils.getCurrentUserId();
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<DocumentDto> results = documentService.searchDocuments(
                userId,
                query,
                categoryId,
                ownerId,
                fromDate,
                toDate,
                pageRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document metadata details")
    public ResponseEntity<ApiResponse<DocumentDto>> getDocumentDetails(
            @PathVariable String id,
            HttpServletRequest request) {

        String userId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        DocumentDto details = documentService.getDocumentDetails(id, userId, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok(details));
    }

    @GetMapping("/documents/{id}/download")
    @Operation(summary = "Download private document content (authorized read access required)")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String id,
            HttpServletRequest request) {

        String userId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        DocumentService.DownloadPayload payload = documentService.downloadDocument(id, userId, clientIp, userAgent);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(payload.fileSize()))
                .body(payload.resource());
    }

    @GetMapping("/documents/{id}/preview")
    @Operation(summary = "Preview private document content in-browser (authorized VIEW, READ, EDIT, or OWNER required)")
    public ResponseEntity<Resource> previewDocument(
            @PathVariable String id,
            HttpServletRequest request) {

        String userId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        DocumentService.DownloadPayload payload = documentService.previewDocument(id, userId, clientIp, userAgent);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + payload.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(payload.fileSize()))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(payload.resource());
    }

    @PutMapping("/documents/{id}")
    @Operation(summary = "Update document metadata (requires EDIT or OWNER)")
    public ResponseEntity<ApiResponse<DocumentDto>> updateMetadata(
            @PathVariable String id,
            @Valid @RequestBody DocumentUpdateRequest updateRequest,
            HttpServletRequest request) {

        String userId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        DocumentDto updated = documentService.updateMetadata(id, updateRequest, userId, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Document metadata updated successfully", updated));
    }

    @PostMapping(value = "/documents/{id}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace document file content and increment version (requires EDIT or OWNER)")
    public ResponseEntity<ApiResponse<DocumentDto>> replaceContent(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        String userId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        DocumentDto updated = documentService.replaceContent(id, file, userId, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Document content replaced successfully", updated));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Delete document permanently (Owner only)")
    public ResponseEntity<ApiResponse<String>> deleteDocument(
            @PathVariable String id,
            HttpServletRequest request) {

        String userId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        documentService.deleteDocument(id, userId, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Document deleted successfully"));
    }

    @GetMapping("/categories")
    @Operation(summary = "List document categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getAllCategories()));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create document category (Admin only)")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        String actorId = SecurityUtils.getCurrentUserId();
        String actorUsername = SecurityUtils.getCurrentUsername();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        CategoryDto created = documentService.createCategory(name, description, actorId, actorUsername, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Category created successfully", created));
    }
}
