package com.campus.sportsbuddy.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealtimePresenceService {

    private static final Duration ONLINE_TTL = Duration.ofMinutes(2);

    private final Map<Long, Instant> lastActiveByUser = new ConcurrentHashMap<>();

    public void markActive(Long userId) {
        if (userId != null) {
            lastActiveByUser.put(userId, Instant.now());
        }
    }

    public boolean isOnline(Long userId) {
        Instant lastActive = lastActiveByUser.get(userId);
        return lastActive != null && lastActive.isAfter(Instant.now().minus(ONLINE_TTL));
    }

    public int onlineCount() {
        Instant threshold = Instant.now().minus(ONLINE_TTL);
        lastActiveByUser.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
        return lastActiveByUser.size();
    }
}
