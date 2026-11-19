package com.b2bfintech.platform.service;

import com.b2bfintech.common.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window login rate limiter protecting {@code /api/v1/auth/password/login} against
 * brute-force and credential-stuffing attacks.
 */
@Component
public class LoginAttemptLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_IDENTIFIER_FAILURES = 5;
    private static final int MAX_IP_FAILURES = 10;

    private final ConcurrentHashMap<String, Deque<Instant>> identifierFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> ipFailures = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public void assertAllowed(String identifier, String ipAddress) {
        Instant now = clock.instant();
        String idKey = normalizeIdentifier(identifier);
        if (idKey != null && countRecent(identifierFailures.get(idKey), now) >= MAX_IDENTIFIER_FAILURES) {
            throw new TooManyRequestsException("Too many failed sign-in attempts. Please wait 15 minutes and try again.");
        }
        String ipKey = normalizeIp(ipAddress);
        if (ipKey != null && countRecent(ipFailures.get(ipKey), now) >= MAX_IP_FAILURES) {
            throw new TooManyRequestsException("Too many failed sign-in attempts. Please wait 15 minutes and try again.");
        }
    }

    public void recordFailure(String identifier, String ipAddress) {
        Instant now = clock.instant();
        String idKey = normalizeIdentifier(identifier);
        if (idKey != null) {
            record(identifierFailures, idKey, now);
        }
        String ipKey = normalizeIp(ipAddress);
        if (ipKey != null) {
            record(ipFailures, ipKey, now);
        }
    }

    public void clearIdentifier(String identifier) {
        String idKey = normalizeIdentifier(identifier);
        if (idKey != null) {
            identifierFailures.remove(idKey);
        }
    }

    private static void record(ConcurrentHashMap<String, Deque<Instant>> map, String key, Instant now) {
        Deque<Instant> deque = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque, now);
            deque.addLast(now);
        }
    }

    private static int countRecent(Deque<Instant> deque, Instant now) {
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            prune(deque, now);
            return deque.size();
        }
    }

    private static void prune(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return ip.trim();
    }
}
