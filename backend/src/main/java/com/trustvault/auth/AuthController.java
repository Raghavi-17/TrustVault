package com.trustvault.auth;

import com.trustvault.audit.AuditOutcome;
import com.trustvault.audit.AuditService;
import com.trustvault.audit.AuditEventType;
import com.trustvault.common.ApiResponse;
import com.trustvault.common.BadRequestException;
import com.trustvault.common.RateLimitExceededException;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Session-based authentication and CSRF management")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final RateLimiterService rateLimiterService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            RateLimiterService rateLimiterService,
            AuditService auditService,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.rateLimiterService = rateLimiterService;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and establish HTTP-only session")
    public ResponseEntity<ApiResponse<AuthUserResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String clientIp = SecurityUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String rateLimitKey = clientIp + ":" + request.username().toLowerCase();

        if (rateLimiterService.isBlocked(rateLimitKey)) {
            auditService.recordEvent(
                    null,
                    request.username(),
                    AuditEventType.LOGIN_FAILURE,
                    "USER",
                    null,
                    AuditOutcome.DENIED,
                    clientIp,
                    userAgent,
                    null,
                    "Login rejected due to rate limiting lockout"
            );
            throw new RateLimitExceededException("Account temporarily locked due to multiple failed login attempts. Please try again after 15 minutes.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            // Set Spring Security Context
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            // Establish secure server-side session
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            rateLimiterService.reset(rateLimitKey);

            auditService.recordEvent(
                    userDetails.getId(),
                    userDetails.getUsername(),
                    AuditEventType.LOGIN_SUCCESS,
                    "USER",
                    userDetails.getId(),
                    AuditOutcome.SUCCESS,
                    clientIp,
                    userAgent,
                    session.getId(),
                    "Successful authentication for user: " + userDetails.getUsername()
            );

            AuthUserResponse responseData = AuthUserResponse.fromUserDetails(userDetails);
            return ResponseEntity.ok(ApiResponse.ok("Login successful", responseData));

        } catch (BadCredentialsException ex) {
            rateLimiterService.recordFailure(rateLimitKey);
            auditService.recordEvent(
                    null,
                    request.username(),
                    AuditEventType.LOGIN_FAILURE,
                    "USER",
                    null,
                    AuditOutcome.FAILED,
                    clientIp,
                    userAgent,
                    null,
                    "Invalid credentials provided for username: " + request.username()
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.ok("Invalid username or password", null));
        } catch (DisabledException ex) {
            auditService.recordEvent(
                    null,
                    request.username(),
                    AuditEventType.LOGIN_FAILURE,
                    "USER",
                    null,
                    AuditOutcome.DENIED,
                    clientIp,
                    userAgent,
                    null,
                    "Login attempt on deactivated account: " + request.username()
            );
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.ok("Account is deactivated. Please contact an administrator.", null));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate session and log out")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        String username = SecurityUtils.getCurrentUsername();
        String clientIp = SecurityUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        HttpSession session = request.getSession(false);
        String sessionId = session != null ? session.getId() : null;

        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        if (userId != null) {
            auditService.recordEvent(
                    userId,
                    username,
                    AuditEventType.LOGOUT,
                    "USER",
                    userId,
                    AuditOutcome.SUCCESS,
                    clientIp,
                    userAgent,
                    sessionId,
                    "User logged out successfully"
            );
        }

        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get currently authenticated user details")
    public ResponseEntity<ApiResponse<AuthUserResponse>> getCurrentUser() {
        CustomUserDetails userDetails = SecurityUtils.getRequiredAuthenticatedUser();
        // Re-verify against database in case account was deactivated mid-session
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new BadRequestException("User record not found"));

        if (!user.isActive()) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.ok("Account is deactivated", null));
        }

        return ResponseEntity.ok(ApiResponse.ok(AuthUserResponse.fromUser(user)));
    }

    @GetMapping("/csrf")
    @Operation(summary = "Retrieve CSRF token for state-changing operations")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> getCsrfToken(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            return ResponseEntity.ok(ApiResponse.ok(new CsrfTokenResponse(
                    token.getToken(),
                    token.getHeaderName(),
                    token.getParameterName()
            )));
        }
        return ResponseEntity.ok(ApiResponse.ok(new CsrfTokenResponse("", "X-CSRF-TOKEN", "_csrf")));
    }
}
