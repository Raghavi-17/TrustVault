package com.trustvault.user;

import com.trustvault.auth.SecurityUtils;
import com.trustvault.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User directory and administrative account management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all registered users (for sharing and user directory)")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getAllUsers()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable String id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok(UserDto.fromEntity(user)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user account (Admin only)")
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest request) {
        String actorId = SecurityUtils.getCurrentUserId();
        String actorUsername = SecurityUtils.getCurrentUsername();
        UserDto created = userService.createUser(request, actorId, actorUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("User created successfully", created));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a user account (Admin only)")
    public ResponseEntity<ApiResponse<UserDto>> updateUserStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        String actorId = SecurityUtils.getCurrentUserId();
        String actorUsername = SecurityUtils.getCurrentUsername();
        UserDto updated = userService.updateUserStatus(id, request.isActive(), actorId, actorUsername);
        return ResponseEntity.ok(ApiResponse.ok("User status updated successfully", updated));
    }
}
