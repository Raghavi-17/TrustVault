package com.trustvault.admin;

import com.trustvault.auth.SecurityUtils;
import com.trustvault.common.ApiResponse;
import com.trustvault.user.Role;
import com.trustvault.user.UserDto;
import com.trustvault.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN')")
@Tag(name = "Administration", description = "Administrative dashboard and oversight")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    public AdminController(AdminService adminService, UserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get aggregated administrative security metrics and activity (Admin only)")
    public ResponseEntity<ApiResponse<AdminDashboardStatsDto>> getDashboardStats() {
        AdminDashboardStatsDto stats = adminService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @GetMapping("/registrations/pending")
    @Operation(summary = "List all registration requests pending administrator review (Admin only)")
    public ResponseEntity<ApiResponse<List<UserDto>>> getPendingRegistrations() {
        List<UserDto> pending = userService.getPendingUsers();
        return ResponseEntity.ok(ApiResponse.ok(pending));
    }

    @PostMapping("/registrations/{id}/approve")
    @Operation(summary = "Approve a pending user registration request (Admin only)")
    public ResponseEntity<ApiResponse<UserDto>> approveRegistration(
            @PathVariable String id,
            @RequestBody(required = false) ApproveRegistrationRequest request,
            HttpServletRequest httpRequest) {
        String adminId = SecurityUtils.getCurrentUserId();
        String adminUsername = SecurityUtils.getCurrentUsername();
        String clientIp = SecurityUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        Role assignedRole = (request != null && request.role() != null) ? request.role() : Role.VIEWER;

        UserDto approved = userService.approveRegistration(id, assignedRole, adminId, adminUsername, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Registration approved successfully", approved));
    }

    @PostMapping("/registrations/{id}/reject")
    @Operation(summary = "Reject a pending user registration request (Admin only)")
    public ResponseEntity<ApiResponse<UserDto>> rejectRegistration(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        String adminId = SecurityUtils.getCurrentUserId();
        String adminUsername = SecurityUtils.getCurrentUsername();
        String clientIp = SecurityUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        UserDto rejected = userService.rejectRegistration(id, adminId, adminUsername, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Registration rejected", rejected));
    }
}
