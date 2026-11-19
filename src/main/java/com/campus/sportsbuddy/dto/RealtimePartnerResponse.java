package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.SkillLevel;

import java.util.List;

public record RealtimePartnerResponse(
        Long userId,
        String displayName,
        SkillLevel skillLevel,
        String campusZone,
        Integer creditScore,
        Double distanceKm,
        List<String> sportPreferences
) {
}
