package com.trustvault.config;

import com.trustvault.audit.AuditEvent;
import com.trustvault.audit.AuditEventRepository;
import com.trustvault.audit.AuditEventType;
import com.trustvault.audit.AuditOutcome;
import com.trustvault.audit.AuditService;
import com.trustvault.document.Category;
import com.trustvault.document.CategoryRepository;
import com.trustvault.user.Role;
import com.trustvault.user.User;
import com.trustvault.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${trustvault.admin.username:admin}")
    private String adminUsername;

    @org.springframework.beans.factory.annotation.Value("${trustvault.admin.password:Admin@TrustVault2026!}")
    private String adminPassword;

    @org.springframework.beans.factory.annotation.Value("${trustvault.admin.email:admin@trustvault.local}")
    private String adminEmail;

    @org.springframework.beans.factory.annotation.Value("${trustvault.admin.fullname:System Administrator}")
    private String adminFullName;

    public DataInitializer(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedCategories();
        seedUsers();
        seedGenesisAuditBlock();
    }

    private void seedCategories() {
        if (categoryRepository.count() == 0) {
            log.info("Seeding default categories...");
            categoryRepository.saveAll(List.of(
                    new Category("Finance", "Financial records, budgets, statements, and tax filings"),
                    new Category("Legal & Compliance", "Contracts, NDA agreements, policies, and regulatory documents"),
                    new Category("Engineering", "Architecture diagrams, security reviews, and technical specifications"),
                    new Category("Operations", "Standard operating procedures, vendor reports, and internal guidelines")
            ));
        }
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            log.info("Seeding initial administrator and demo accounts...");

            User admin = new User(
                    adminUsername,
                    passwordEncoder.encode(adminPassword),
                    adminFullName,
                    adminEmail,
                    Role.ADMIN,
                    com.trustvault.user.UserStatus.ACTIVE
            );

            User alice = new User(
                    "owner_alice",
                    passwordEncoder.encode("Owner@TrustVault2026!"),
                    "Alice Jenkins (Owner)",
                    "alice@trustvault.local",
                    Role.OWNER,
                    com.trustvault.user.UserStatus.ACTIVE
            );

            User bob = new User(
                    "owner_bob",
                    passwordEncoder.encode("OwnerBob@TrustVault2026!"),
                    "Bob Martinez (Owner)",
                    "bob@trustvault.local",
                    Role.OWNER,
                    com.trustvault.user.UserStatus.ACTIVE
            );

            User carol = new User(
                    "viewer_carol",
                    passwordEncoder.encode("Viewer@TrustVault2026!"),
                    "Carol Vance (Viewer)",
                    "carol@trustvault.local",
                    Role.VIEWER,
                    com.trustvault.user.UserStatus.ACTIVE
            );

            userRepository.saveAll(List.of(admin, alice, bob, carol));
            log.info("Seed users created successfully (admin: {})", adminUsername);
        }
    }

    private void seedGenesisAuditBlock() {
        if (auditEventRepository.count() == 0) {
            log.info("Initializing Audit Trail Genesis Block...");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            String details = "TrustVault Genesis Block Initialized. Cryptographic chain started.";
            String genesisHash = AuditService.computeHash(
                    1L,
                    AuditEvent.GENESIS_PREV_HASH,
                    now,
                    "SYSTEM",
                    AuditEventType.AUDIT_INTEGRITY_VERIFY,
                    "SYSTEM",
                    "GENESIS",
                    AuditOutcome.SUCCESS,
                    details
            );

            AuditEvent genesis = new AuditEvent(
                    1L,
                    "SYSTEM",
                    "SYSTEM",
                    AuditEventType.AUDIT_INTEGRITY_VERIFY,
                    "SYSTEM",
                    "GENESIS",
                    AuditOutcome.SUCCESS,
                    "127.0.0.1",
                    "TrustVault Core",
                    "GENESIS-INIT",
                    details,
                    AuditEvent.GENESIS_PREV_HASH,
                    genesisHash
            );
            genesis.setTimestamp(now);
            genesis.setTimestampEpochMs(now.toEpochMilli());
            auditEventRepository.saveAndFlush(genesis);
            log.info("Audit Genesis Block stored with hash: {}", genesisHash);
        }
    }
}
