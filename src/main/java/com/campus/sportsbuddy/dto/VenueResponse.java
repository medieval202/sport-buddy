package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.Venue;

import java.time.LocalTime;

public record VenueResponse(
        Long id,
        String name,
        String campusZone,
        String address,
        LocalTime openTime,
        LocalTime closeTime,
        boolean indoor
) {
    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getCampusZone(),
                venue.getAddress(),
                venue.getOpenTime(),
                venue.getCloseTime(),
                venue.isIndoor()
        );
    }
}
