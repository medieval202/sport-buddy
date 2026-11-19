package com.campus.sportsbuddy.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record RealtimeMatchResponse(
        String matchId,
        String status,
        String sportType,
        String timeWindow,
        RealtimePartnerResponse partner,
        Integer matchScore,
        Map<String, Integer> scoreBreakdown,
        List<String> highlights,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
    public static RealtimeMatchResponse none() {
        return new RealtimeMatchResponse(
                null,
                "NONE",
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                null,
                null
        );
    }
}
