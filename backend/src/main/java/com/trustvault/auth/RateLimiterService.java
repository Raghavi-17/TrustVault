package com.trustvault.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 15 * 60; // 15 minutes

    private static class AttemptInfo {
        int attempts;
        Instant firstAttemptTime;
        Instant lockedUntil;

        AttemptInfo() {
            this.attempts = 1;
            this.firstAttemptTime = Instant.now();
            this.lockedUntil = null;
        }
    }

    private final Map<String, AttemptInfo> loginAttempts = new ConcurrentHashMap<>();

    public synchronized boolean isBlocked(String key) {
        AttemptInfo info = loginAttempts.get(key);
        if (info == null) {
            return false;
        }
        if (info.lockedUntil != null) {
            if (Instant.now().isBefore(info.lockedUntil)) {
                return true;
            } else {
                // Lock expired
                loginAttempts.remove(key);
                return false;
            }
        }
        // If window passed (e.g. 15 minutes since first attempt), reset
        if (Instant.now().isAfter(info.firstAttemptTime.plusSeconds(LOCKOUT_DURATION_SECONDS))) {
            loginAttempts.remove(key);
            return false;
        }
        return false;
    }

    public synchronized void recordFailure(String key) {
        AttemptInfo info = loginAttempts.computeIfAbsent(key, k -> new AttemptInfo());
        info.attempts++;
        if (info.attempts >= MAX_FAILED_ATTEMPTS) {
            info.lockedUntil = Instant.now().plusSeconds(LOCKOUT_DURATION_SECONDS);
        }
    }

    public synchronized void reset(String key) {
        loginAttempts.remove(key);
    }
}
