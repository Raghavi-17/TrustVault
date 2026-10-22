package com.trustvault.user;

import com.trustvault.audit.AuditOutcome;
import com.trustvault.audit.AuditService;
import com.trustvault.audit.AuditEventType;
import com.trustvault.common.AccountDeactivatedException;
import com.trustvault.common.BadRequestException;
import com.trustvault.common.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Transactional
    public UserDto createUser(CreateUserRequest request, String actorId, String actorUsername) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BadRequestException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email '" + request.email() + "' is already registered");
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.email(),
                request.role()
        );

        User saved = userRepository.save(user);

        auditService.recordEvent(
                actorId,
                actorUsername,
                AuditEventType.ACCOUNT_ACTIVATE,
                "USER",
                saved.getId(),
                AuditOutcome.SUCCESS,
                null,
                null,
                null,
                "Created user account with role: " + saved.getRole()
        );

        return UserDto.fromEntity(saved);
    }

    @Transactional
    public UserDto updateUserStatus(String userId, boolean isActive, String actorId, String actorUsername) {
        User user = getUserById(userId);

        if (user.getRole() == Role.ADMIN && !isActive) {
            long activeAdminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.ADMIN && u.isActive())
                    .count();
            if (activeAdminCount <= 1) {
                throw new BadRequestException("Cannot deactivate the only remaining active administrator");
            }
        }

        user.setActive(isActive);
        User updated = userRepository.save(user);

        AuditEventType action = isActive ? AuditEventType.ACCOUNT_ACTIVATE : AuditEventType.ACCOUNT_DEACTIVATE;
        auditService.recordEvent(
                actorId,
                actorUsername,
                action,
                "USER",
                userId,
                AuditOutcome.SUCCESS,
                null,
                null,
                null,
                (isActive ? "Activated" : "Deactivated") + " user account: " + user.getUsername()
        );

        return UserDto.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public void verifyAccountActive(String userId) {
        User user = getUserById(userId);
        if (!user.isActive()) {
            throw new AccountDeactivatedException("Your account has been deactivated. Access is denied.");
        }
    }
}
