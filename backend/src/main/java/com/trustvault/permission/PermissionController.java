package com.trustvault.permission;

import com.trustvault.auth.SecurityUtils;
import com.trustvault.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/permissions")
@Tag(name = "Permissions", description = "Document sharing and permission revocation")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @Operation(summary = "List permission grants for a document (Owner only)")
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getDocumentPermissions(
            @PathVariable String documentId,
            HttpServletRequest request) {

        String actorId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        List<PermissionDto> permissions = permissionService.getDocumentPermissions(documentId, actorId, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok(permissions));
    }

    @PostMapping
    @Operation(summary = "Grant access to an existing user (Owner only)")
    public ResponseEntity<ApiResponse<PermissionDto>> grantPermission(
            @PathVariable String documentId,
            @Valid @RequestBody GrantPermissionRequest grantRequest,
            HttpServletRequest request) {

        String actorId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        PermissionDto granted = permissionService.grantPermission(documentId, grantRequest, actorId, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Permission granted successfully", granted));
    }

    @PutMapping("/{targetUserId}")
    @Operation(summary = "Modify permission level for a user (Owner only)")
    public ResponseEntity<ApiResponse<PermissionDto>> updatePermission(
            @PathVariable String documentId,
            @PathVariable String targetUserId,
            @Valid @RequestBody UpdatePermissionRequest updateRequest,
            HttpServletRequest request) {

        String actorId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        PermissionDto updated = permissionService.updatePermission(documentId, targetUserId, updateRequest, actorId, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Permission updated successfully", updated));
    }

    @DeleteMapping("/{targetUserId}")
    @Operation(summary = "Immediately revoke document access for a user (Owner only)")
    public ResponseEntity<ApiResponse<String>> revokePermission(
            @PathVariable String documentId,
            @PathVariable String targetUserId,
            HttpServletRequest request) {

        String actorId = SecurityUtils.getCurrentUserId();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        permissionService.revokePermission(documentId, targetUserId, actorId, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Access revoked successfully"));
    }
}
