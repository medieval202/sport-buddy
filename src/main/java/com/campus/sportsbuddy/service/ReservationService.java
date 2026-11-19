package com.campus.sportsbuddy.service;

import com.campus.sportsbuddy.dto.BusySlotResponse;
import com.campus.sportsbuddy.dto.ReservationRequest;
import com.campus.sportsbuddy.dto.ReservationResponse;
import com.campus.sportsbuddy.dto.VenueResponse;
import com.campus.sportsbuddy.model.Activity;
import com.campus.sportsbuddy.model.Reservation;
import com.campus.sportsbuddy.model.ReservationStatus;
import com.campus.sportsbuddy.model.User;
import com.campus.sportsbuddy.model.Venue;
import com.campus.sportsbuddy.repository.ActivityRepository;
import com.campus.sportsbuddy.repository.ReservationRepository;
import com.campus.sportsbuddy.repository.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReservationService {

    private final VenueRepository venueRepository;
    private final ReservationRepository reservationRepository;
    private final ActivityRepository activityRepository;
    private final UserService userService;

    public ReservationService(
            VenueRepository venueRepository,
            ReservationRepository reservationRepository,
            ActivityRepository activityRepository,
            UserService userService
    ) {
        this.venueRepository = venueRepository;
        this.reservationRepository = reservationRepository;
        this.activityRepository = activityRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> venues() {
        return venueRepository.findByEnabledTrueOrderByNameAsc()
                .stream()
                .map(VenueResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> myReservations() {
        User user = userService.currentUser();
        return reservationRepository
                .findByStudentIdOrderByReservationDateDescStartTimeDesc(user.getId())
                .stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusySlotResponse> busySlots(Long venueId, LocalDate date) {
        return reservationRepository.findByVenueIdAndReservationDateAndStatus(
                        venueId, date, ReservationStatus.CONFIRMED)
                .stream()
                .map(reservation -> new BusySlotResponse(
                        reservation.getStartTime(),
                        reservation.getEndTime()
                ))
                .toList();
    }

    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        if (Duration.between(request.startTime(), request.endTime()).toMinutes() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单次预约最长 2 小时");
        }
        if (request.reservationDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能预约过去的日期");
        }

        // 对场地行加悲观锁，让同一场地的并发预约串行检查，避免两个请求同时抢到同一时段。
        Venue venue = venueRepository.findByIdForUpdate(request.venueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "场地不存在"));
        if (!venue.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "场地暂不可用");
        }
        if (request.startTime().isBefore(venue.getOpenTime())
                || request.endTime().isAfter(venue.getCloseTime())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预约时间需在场地开放时间 " + venue.getOpenTime() + " - " + venue.getCloseTime() + " 内"
            );
        }

        boolean overlaps = reservationRepository.findByVenueIdAndReservationDateAndStatus(
                        venue.getId(), request.reservationDate(), ReservationStatus.CONFIRMED)
                .stream()
                .anyMatch(existing -> request.startTime().isBefore(existing.getEndTime())
                        && request.endTime().isAfter(existing.getStartTime()));
        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该时段已被预约，请选择其他时间");
        }

        Activity activity = null;
        if (request.activityId() != null) {
            activity = activityRepository.findById(request.activityId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在"));
        }

        Reservation reservation = new Reservation();
        reservation.setVenue(venue);
        reservation.setStudent(userService.currentUser());
        reservation.setActivity(activity);
        reservation.setReservationDate(request.reservationDate());
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Transactional
    public void cancel(Long reservationId) {
        User user = userService.currentUser();
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "预约不存在"));
        if (!reservation.getStudent().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能取消他人的预约");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
    }
}
