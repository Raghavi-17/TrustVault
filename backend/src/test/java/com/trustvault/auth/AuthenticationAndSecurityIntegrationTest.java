package com.trustvault.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustvault.user.Role;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthenticationAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
        userRepository.save(new User("testadmin", passwordEncoder.encode("AdminPass123!"), "Admin Test", "admin@test.local", Role.ADMIN));
        userRepository.save(new User("testviewer", passwordEncoder.encode("ViewerPass123!"), "Viewer Test", "viewer@test.local", Role.VIEWER));
    }

    @Test
    @DisplayName("Test 1: Unauthenticated request to /api/documents returns 401 Unauthorized")
    public void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Test: Invalid login credentials returns 401 with safe error message")
    public void testInvalidLoginReturns401() throws Exception {
        LoginRequest req = new LoginRequest("testviewer", "WrongPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test: Valid credentials returns 200 OK with authenticated user profile")
    public void testValidLoginSuccess() throws Exception {
        LoginRequest req = new LoginRequest("testviewer", "ViewerPass123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testviewer"))
                .andExpect(jsonPath("$.data.role").value("VIEWER"));
    }

    @Test
    @DisplayName("Test 18: Admin-only endpoint /api/admin/dashboard returns 403 when accessed by VIEWER")
    @WithMockUser(username = "testviewer", roles = {"VIEWER"})
    public void testAdminEndpointForbiddenForViewer() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 18: Admin-only endpoint /api/admin/dashboard succeeds for ADMIN")
    @WithMockUser(username = "testadmin", roles = {"ADMIN"})
    public void testAdminEndpointAllowedForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").isNumber());
    }

    @Test
    @DisplayName("Health endpoint is accessible without authentication")
    public void testHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
