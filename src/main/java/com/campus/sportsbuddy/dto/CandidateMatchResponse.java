package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.SkillLevel;

import java.util.List;
import java.util.Map;

public record CandidateMatchResponse(
        Long userId,
        String displayName,
        SkillLevel skillLevel,
        String campusZone,
        int creditScore,
        double distanceKm,
        int matchScore,
        Map<String, Integer> scoreBreakdown,
        List<String> highlights
) {
}
