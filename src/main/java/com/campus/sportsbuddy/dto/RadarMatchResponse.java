package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.RadarMatchStatus;

import java.time.LocalDateTime;

public record RadarMatchResponse(
        Long matchId,
        RadarMatchStatus status,
        String sportType,
        Integer matchScore,
        Long requesterId,
        String requesterName,
        Long candidateId,
        String candidateName,
        LocalDateTime createdAt,
        String message
) {
}
