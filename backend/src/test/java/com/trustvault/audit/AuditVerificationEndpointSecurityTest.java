package com.trustvault.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuditVerificationEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    public void setup() {
        auditService.resetCheckpoints();
        auditEventRepository.deleteAll();
        auditService.recordEvent(
                "user-1", "alice", AuditEventType.LOGIN_SUCCESS, "USER", "user-1",
                AuditOutcome.SUCCESS, "127.0.0.1", "TestAgent", "corr-1", "Alice logged in"
        );
    }

    @Test
    @DisplayName("Endpoint Security: Unauthenticated request to GET /api/audit/verify is rejected (401)")
    public void testUnauthenticatedCannotAccessVerification() throws Exception {
        mockMvc.perform(get("/api/audit/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "viewer_carol", roles = {"VIEWER"})
    @DisplayName("Endpoint Security: Authenticated viewer can perform read-only GET /api/audit/verify")
    public void testAuthenticatedViewerCanVerifyChain() throws Exception {
        mockMvc.perform(get("/api/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CHAIN_INTACT"))
                .andExpect(jsonPath("$.data.isChainValid").value(true))
                .andExpect(jsonPath("$.data.totalEventsChecked").value(1));
    }

    @Test
    @WithMockUser(username = "viewer_carol", roles = {"VIEWER"})
    @DisplayName("Endpoint Security: Non-admin viewer is forbidden from POST /api/audit/verify (403)")
    public void testNonAdminCannotPostVerify() throws Exception {
        mockMvc.perform(post("/api/audit/verify").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Endpoint Security: Admin can perform POST /api/audit/verify")
    public void testAdminCanPostVerify() throws Exception {
        mockMvc.perform(post("/api/audit/verify").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CHAIN_INTACT"))
                .andExpect(jsonPath("$.data.isChainValid").value(true));
    }
}
