package com.campus.sportsbuddy.controller;

import com.campus.sportsbuddy.dto.BusySlotResponse;
import com.campus.sportsbuddy.dto.ReservationRequest;
import com.campus.sportsbuddy.dto.ReservationResponse;
import com.campus.sportsbuddy.dto.VenueResponse;
import com.campus.sportsbuddy.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class VenueController {

    private final ReservationService reservationService;

    public VenueController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/venues")
    public List<VenueResponse> venues() {
        return reservationService.venues();
    }

    @GetMapping("/venues/{id}/busy")
    public List<BusySlotResponse> busySlots(
            @PathVariable Long id,
            @RequestParam LocalDate date
    ) {
        return reservationService.busySlots(id, date);
    }

    @GetMapping("/reservations/mine")
    public List<ReservationResponse> mine() {
        return reservationService.myReservations();
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@Valid @RequestBody ReservationRequest request) {
        return reservationService.reserve(request);
    }

    @DeleteMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        reservationService.cancel(id);
    }
}
