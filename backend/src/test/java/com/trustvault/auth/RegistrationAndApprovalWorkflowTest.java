package com.trustvault.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustvault.admin.ApproveRegistrationRequest;
import com.trustvault.audit.AuditEventRepository;
import com.trustvault.audit.AuditEventType;
import com.trustvault.user.Role;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import com.trustvault.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RegistrationAndApprovalWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
        // Setup initial admin and test viewer
        userRepository.save(new User(
                "sysadmin",
                passwordEncoder.encode("AdminPass123!"),
                "System Admin",
                "admin@vault.local",
                Role.ADMIN,
                UserStatus.ACTIVE
        ));
    }

    @Test
    @DisplayName("Registration: Public registration successfully creates PENDING account")
    public void testRegistrationCreatesPendingAccount() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Dana White",
                "dana@example.com",
                "danawhite",
                "SecurePassword123!",
                "SecurePassword123!"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration submitted successfully. Your account is pending administrator approval."))
                .andExpect(jsonPath("$.data.username").value("danawhite"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.role").value("VIEWER"))
                .andExpect(jsonPath("$.data.isActive").value(false));

        User user = userRepository.findByUsername("danawhite").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getRole()).isEqualTo(Role.VIEWER);
        assertThat(user.isActive()).isFalse();

        // Audit trail records REGISTRATION_REQUEST
        boolean hasAudit = auditEventRepository.findAll().stream()
                .anyMatch(e -> e.getAction() == AuditEventType.REGISTRATION_REQUEST && "danawhite".equals(e.getActorUsername()));
        assertThat(hasAudit).isTrue();
    }

    @Test
    @DisplayName("Registration: Fails when passwords do not match")
    public void testRegistrationFailsPasswordMismatch() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Mismatch User",
                "mismatch@example.com",
                "mismatch",
                "Password123!",
                "Different123!"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password and confirm password do not match"));
    }

    @Test
    @DisplayName("Registration: Fails on duplicate email")
    public void testRegistrationFailsDuplicateEmail() throws Exception {
        RegisterRequest request1 = new RegisterRequest(
                "User One",
                "duplicate@example.com",
                "userone",
                "Password123!",
                "Password123!"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        RegisterRequest request2 = new RegisterRequest(
                "User Two",
                "duplicate@example.com",
                "usertwo",
                "Password123!",
                "Password123!"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email 'duplicate@example.com' is already registered"));
    }

    @Test
    @DisplayName("Login Denial: Pending user cannot log in and receives informative message")
    public void testPendingUserCannotLogin() throws Exception {
        userRepository.save(new User(
                "pendinguser",
                passwordEncoder.encode("Password123!"),
                "Pending Person",
                "pending@example.com",
                Role.VIEWER,
                UserStatus.PENDING
        ));

        LoginRequest loginRequest = new LoginRequest("pendinguser", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your registration request is pending administrator approval."));
    }

    @Test
    @DisplayName("Login Denial: Rejected user cannot log in and receives informative message")
    public void testRejectedUserCannotLogin() throws Exception {
        userRepository.save(new User(
                "rejecteduser",
                passwordEncoder.encode("Password123!"),
                "Rejected Person",
                "rejected@example.com",
                Role.VIEWER,
                UserStatus.REJECTED
        ));

        LoginRequest loginRequest = new LoginRequest("rejecteduser", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account registration has been rejected. Please contact an administrator."));
    }

    @Test
    @DisplayName("Admin Oversight: Non-admin cannot access pending registrations")
    @WithMockUser(username = "regularuser", roles = {"VIEWER"})
    public void testNonAdminCannotAccessPendingRegistrations() throws Exception {
        mockMvc.perform(get("/api/admin/registrations/pending"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin Oversight: Admin can list pending registrations")
    @WithMockUser(username = "sysadmin", roles = {"ADMIN"})
    public void testAdminCanListPendingRegistrations() throws Exception {
        userRepository.save(new User(
                "pending1",
                passwordEncoder.encode("Password123!"),
                "Pending 1",
                "p1@example.com",
                Role.VIEWER,
                UserStatus.PENDING
        ));

        mockMvc.perform(get("/api/admin/registrations/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("pending1"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("Admin Approval: Admin approves pending registration and user can subsequently log in")
    public void testAdminApprovalAllowsLogin() throws Exception {
        User pending = userRepository.save(new User(
                "futureactive",
                passwordEncoder.encode("Password123!"),
                "Future Active",
                "future@example.com",
                Role.VIEWER,
                UserStatus.PENDING
        ));

        // First attempt login -> fails 403
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("futureactive", "Password123!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your registration request is pending administrator approval."));

        // Admin approves
        mockMvc.perform(post("/api/admin/registrations/" + pending.getId() + "/approve")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("sysadmin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRegistrationRequest(Role.OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));

        // Second attempt login -> succeeds 200
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("futureactive", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("futureactive"))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Admin Rejection: Admin rejects pending registration")
    public void testAdminRejection() throws Exception {
        User pending = userRepository.save(new User(
                "futurereject",
                passwordEncoder.encode("Password123!"),
                "Future Reject",
                "reject@example.com",
                Role.VIEWER,
                UserStatus.PENDING
        ));

        mockMvc.perform(post("/api/admin/registrations/" + pending.getId() + "/reject")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("sysadmin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        User rejected = userRepository.findById(pending.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(UserStatus.REJECTED);
        assertThat(rejected.isActive()).isFalse();
    }

    @Test
    @DisplayName("Authorization: Ordinary user receives 403 Forbidden when attempting approval")
    @WithMockUser(username = "regular_viewer", roles = {"VIEWER"})
    public void testOrdinaryUserCannotApproveRegistration() throws Exception {
        User pending = userRepository.save(new User(
                "pendingtarget1",
                passwordEncoder.encode("Password123!"),
                "Pending Target 1",
                "target1@example.com",
                Role.VIEWER,
                UserStatus.PENDING
        ));

        mockMvc.perform(post("/api/admin/registrations/" + pending.getId() + "/approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRegistrationRequest(Role.VIEWER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not possess the required permissions or role for this resource"));

        // User status remains PENDING in database
        User unchanged = userRepository.findById(pending.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("Authorization: Ordinary user receives 403 Forbidden when attempting rejection")
    @WithMockUser(username = "regular_viewer", roles = {"VIEWER"})
    public void testOrdinaryUserCannotRejectRegistration() throws Exception {
        User pending = userRepository.save(new User(
                "pendingtarget2",
                passwordEncoder.encode("Password123!"),
                "Pending Target 2",
                "target2@example.com",
                Role.VIEWER,
                UserStatus.PENDING
        ));

        mockMvc.perform(post("/api/admin/registrations/" + pending.getId() + "/reject")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not possess the required permissions or role for this resource"));

        User unchanged = userRepository.findById(pending.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("Authorization: Unauthenticated request receives 401 Unauthorized")
    public void testUnauthenticatedCannotApproveRegistration() throws Exception {
        mockMvc.perform(post("/api/admin/registrations/some-user-id/approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRegistrationRequest(Role.VIEWER))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Admin cannot assign ADMIN role via registration approval")
    public void testAdminCannotAssignAdminRoleViaApproval() throws Exception {
        User pending = userRepository.save(new User(
                "tryadmin",
                passwordEncoder.encode("Password123!"),
                "Try Admin",
                "tryadmin@example.com",
                Role.VIEWER,
                UserStatus.PENDING
        ));

        mockMvc.perform(post("/api/admin/registrations/" + pending.getId() + "/approve")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("sysadmin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRegistrationRequest(Role.ADMIN))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Administrator privileges cannot be granted via registration approval. Administrators must be provisioned via the secure bootstrap configuration."));

        User unchanged = userRepository.findById(pending.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(UserStatus.PENDING);
    }
}
