package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.Reservation;
import com.campus.sportsbuddy.model.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationResponse(
        Long id,
        Long venueId,
        String venueName,
        String venueAddress,
        LocalDate reservationDate,
        LocalTime startTime,
        LocalTime endTime,
        ReservationStatus status,
        Long activityId,
        String activityTitle
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getVenue().getId(),
                reservation.getVenue().getName(),
                reservation.getVenue().getAddress(),
                reservation.getReservationDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getActivity() == null ? null : reservation.getActivity().getId(),
                reservation.getActivity() == null ? null : reservation.getActivity().getTitle()
        );
    }
}
