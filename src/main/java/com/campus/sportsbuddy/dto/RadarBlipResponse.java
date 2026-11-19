package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.SkillLevel;

import java.util.List;
import java.util.Map;

public record RadarBlipResponse(
        Long userId,
        String displayName,
        SkillLevel skillLevel,
        String campusZone,
        Integer creditScore,
        Double distanceKm,
        Integer sector,
        Double angle,
        Double radiusRatio,
        Integer score,
        Map<String, Integer> scoreBreakdown,
        List<String> highlights,
        Boolean online
) {
}
