package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.ActivityStatus;
import com.campus.sportsbuddy.model.SkillLevel;

import java.time.LocalDateTime;

public record ActivityResponse(
        Long id,
        String title,
        String sportType,
        String description,
        String campusZone,
        String locationName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int maxParticipants,
        int participantCount,
        SkillLevel requiredSkillLevel,
        int minCreditScore,
        ActivityStatus status,
        String rejectReason,
        Long creatorId,
        String creatorName,
        Double latitude,
        Double longitude,
        boolean joined,
        boolean owned
) {
}
