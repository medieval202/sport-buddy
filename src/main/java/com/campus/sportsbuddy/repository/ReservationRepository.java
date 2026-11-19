package com.campus.sportsbuddy.repository;

import com.campus.sportsbuddy.model.Reservation;
import com.campus.sportsbuddy.model.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @EntityGraph(attributePaths = {"venue", "student", "activity"})
    List<Reservation> findByStudentIdOrderByReservationDateDescStartTimeDesc(Long studentId);

    List<Reservation> findByVenueIdAndReservationDateAndStatus(
            Long venueId,
            LocalDate reservationDate,
            ReservationStatus status
    );
}
