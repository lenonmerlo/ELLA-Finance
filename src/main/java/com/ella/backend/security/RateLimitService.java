package com.ella.backend.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
public class RateLimitService {

    private static final long WINDOW_MS = 60L * 60L * 1000L; // 1 hour

    private final ConcurrentHashMap<String, CounterWindow> windows = new ConcurrentHashMap<>();

    public boolean allowForgotPassword(String ip, String email) {
        String safeIp = (ip == null || ip.isBlank()) ? "unknown" : ip.trim();
        String safeEmail = (email == null || email.isBlank()) ? "unknown" : email.trim().toLowerCase();

        boolean ipAllowed = allow("ip:" + safeIp, 20);
        boolean ipEmailAllowed = allow("ip_email:" + safeIp + ":" + safeEmail, 5);
        return ipAllowed && ipEmailAllowed;
    }

    public boolean allowResetPassword(String ip) {
        String safeIp = (ip == null || ip.isBlank()) ? "unknown" : ip.trim();
        return allow("ip:" + safeIp, 20);
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(true);
        CounterWindow w = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                CounterWindow nw = new CounterWindow(now);
                nw.count = 1;
                return nw;
            }
            if (existing.count >= limit) {
                allowed.set(false);
                return existing;
            }
            existing.count++;
            return existing;
        });
        return allowed.get() && w.count <= limit;
    }

    private static class CounterWindow {
        private final long windowStartMs;
        private int count;

        private CounterWindow(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }

        private boolean isExpired(long nowMs) {
            return nowMs - windowStartMs >= WINDOW_MS;
        }
    }
}
