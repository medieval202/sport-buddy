package com.campus.sportsbuddy.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RadarSessionResponse(
        String sessionId,
        String state,
        Integer version,
        Integer currentSector,
        Integer sectors,
        Integer scannedSectors,
        Integer onlineCandidates,
        Double radiusKm,
        Long selectedCandidateId,
        String lastEvent,
        List<RadarBlipResponse> blips,
        LocalDateTime updatedAt
) {
}
