package com.trustvault.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "Application and system health monitoring")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Check application health status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "status", "UP",
                "application", "TrustVault",
                "version", "1.0.0",
                "timestamp", Instant.now().toString()
        )));
    }
}
