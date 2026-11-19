package com.campus.sportsbuddy.service;

import com.campus.sportsbuddy.dto.ActivityRequest;
import com.campus.sportsbuddy.dto.ActivityResponse;
import com.campus.sportsbuddy.dto.AdminReviewRequest;
import com.campus.sportsbuddy.dto.CandidateMatchResponse;
import com.campus.sportsbuddy.model.Activity;
import com.campus.sportsbuddy.model.ActivityStatus;
import com.campus.sportsbuddy.model.Role;
import com.campus.sportsbuddy.model.SkillLevel;
import com.campus.sportsbuddy.model.User;
import com.campus.sportsbuddy.repository.ActivityRepository;
import com.campus.sportsbuddy.repository.UserRepository;
import com.campus.sportsbuddy.websocket.UserNotifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ActivityService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserNotifier userNotifier;

    public ActivityService(
            ActivityRepository activityRepository,
            UserRepository userRepository,
            UserService userService,
            UserNotifier userNotifier
    ) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.userNotifier = userNotifier;
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listApproved(String sport) {
        User current = userService.currentUser();
        return activityRepository.findByStatusOrderByStartTimeAsc(ActivityStatus.APPROVED)
                .stream()
                .filter(activity -> sport == null || sport.isBlank()
                        || activity.getSportType().equalsIgnoreCase(sport))
                .map(activity -> toResponse(activity, current))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listMine() {
        User current = userService.currentUser();
        return activityRepository.findMine(current.getId())
                .stream()
                .map(activity -> toResponse(activity, current))
                .toList();
    }

    @Transactional
    public ActivityResponse create(ActivityRequest request) {
        validateWindow(request.startTime(), request.endTime());
        if (request.startTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "活动开始时间不能早于当前时间");
        }

        User creator = userService.currentUser();
        Activity activity = new Activity();
        activity.setTitle(request.title().trim());
        activity.setSportType(request.sportType().trim());
        activity.setDescription(request.description() == null ? "" : request.description().trim());
        activity.setCampusZone(request.campusZone().trim());
        activity.setLocationName(request.locationName().trim());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setMaxParticipants(request.maxParticipants());
        activity.setRequiredSkillLevel(request.requiredSkillLevel());
        activity.setMinCreditScore(request.minCreditScore());
        activity.setLatitude(request.latitude());
        activity.setLongitude(request.longitude());
        activity.setCreator(creator);
        activity.setStatus(ActivityStatus.PENDING);
        Activity saved = activityRepository.save(activity);

        userRepository.findByRoleOrderByCreatedAtDesc(Role.ADMIN).forEach(admin ->
                userNotifier.sendToUser(
                        admin.getId(),
                        "ACTIVITY_SUBMITTED",
                        creator.getDisplayName() + " 提交了活动《" + saved.getTitle() + "》，请审核",
                        Map.of("activityId", saved.getId())
                ));
        return toResponse(saved, creator);
    }

    @Transactional
    public ActivityResponse join(Long activityId) {
        Activity activity = requireActivity(activityId);
        User current = userService.currentUser();
        if (activity.getCreator().getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能加入自己发起的活动");
        }
        if (activity.getStatus() != ActivityStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动尚未通过审核");
        }
        if (activity.getEndTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动已结束");
        }
        if (current.getCreditScore() < activity.getMinCreditScore()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "信用分不足，无法加入活动");
        }
        if (activity.getParticipants().size() >= activity.getMaxParticipants()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动人数已满");
        }
        if (!activity.getParticipants().add(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你已经加入该活动");
        }
        activityRepository.save(activity);

        if (!activity.getCreator().getId().equals(current.getId())) {
            userNotifier.sendToUser(
                    activity.getCreator().getId(),
                    "ACTIVITY_JOINED",
                    current.getDisplayName() + " 加入了你的活动《" + activity.getTitle() + "》",
                    Map.of("activityId", activity.getId())
            );
        }
        return toResponse(activity, current);
    }

    @Transactional
    public ActivityResponse leave(Long activityId) {
        Activity activity = requireActivity(activityId);
        User current = userService.currentUser();
        if (!activity.getParticipants().remove(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你尚未加入该活动");
        }
        activityRepository.save(activity);
        return toResponse(activity, current);
    }

    @Transactional(readOnly = true)
    public List<CandidateMatchResponse> match(Long activityId, int limit) {
        Activity activity = requireActivity(activityId);
        User current = userService.currentUser();
        Set<Long> excluded = activity.getParticipants().stream().map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        excluded.add(current.getId());
        excluded.add(activity.getCreator().getId());

        return userRepository.findByRoleOrderByCreatedAtDesc(Role.STUDENT)
                .stream()
                .filter(User::isEnabled)
                .filter(user -> !excluded.contains(user.getId()))
                .filter(user -> user.getCreditScore() >= activity.getMinCreditScore())
                .map(user -> score(user, activity))
                .sorted(Comparator.comparingInt(CandidateMatchResponse::matchScore).reversed())
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();
    }

    @Transactional
    public void invite(Long activityId, Long userId) {
        Activity activity = requireActivity(activityId);
        User current = userService.currentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "候选人不存在"));
        if (activity.getStatus() != ActivityStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动尚未通过审核");
        }
        if (target.getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能邀请自己");
        }
        userNotifier.sendToUser(
                target.getId(),
                "ACTIVITY_INVITATION",
                current.getDisplayName() + " 邀请你参加《" + activity.getTitle() + "》",
                Map.of("activityId", activity.getId())
        );
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> pending() {
        User admin = userService.currentUser();
        return activityRepository.findByStatusOrderByCreatedAtDesc(ActivityStatus.PENDING)
                .stream()
                .map(activity -> toResponse(activity, admin))
                .toList();
    }

    @Transactional
    public ActivityResponse review(Long activityId, AdminReviewRequest request) {
        Activity activity = requireActivity(activityId);
        if (activity.getStatus() != ActivityStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该活动已处理");
        }
        if (Boolean.TRUE.equals(request.approved())) {
            activity.setStatus(ActivityStatus.APPROVED);
            activity.setRejectReason(null);
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驳回时请填写原因");
            }
            activity.setStatus(ActivityStatus.REJECTED);
            activity.setRejectReason(request.reason().trim());
        }
        Activity saved = activityRepository.save(activity);
        String message = Boolean.TRUE.equals(request.approved())
                ? "你的活动《" + saved.getTitle() + "》已通过审核"
                : "你的活动《" + saved.getTitle() + "》未通过审核：" + saved.getRejectReason();
        userNotifier.sendToUser(
                saved.getCreator().getId(),
                "ACTIVITY_REVIEWED",
                message,
                Map.of("activityId", saved.getId(), "status", saved.getStatus().name())
        );
        return toResponse(saved, userService.currentUser());
    }

    public Activity requireActivity(Long activityId) {
        return activityRepository.findDetailById(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在"));
    }

    private ActivityResponse toResponse(Activity activity, User current) {
        boolean joined = activity.getParticipants().stream()
                .anyMatch(user -> user.getId().equals(current.getId()));
        return new ActivityResponse(
                activity.getId(),
                activity.getTitle(),
                activity.getSportType(),
                activity.getDescription(),
                activity.getCampusZone(),
                activity.getLocationName(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getMaxParticipants(),
                activity.getParticipants().size(),
                activity.getRequiredSkillLevel(),
                activity.getMinCreditScore(),
                activity.getStatus(),
                activity.getRejectReason(),
                activity.getCreator().getId(),
                activity.getCreator().getDisplayName(),
                activity.getLatitude(),
                activity.getLongitude(),
                joined,
                activity.getCreator().getId().equals(current.getId())
        );
    }

    private CandidateMatchResponse score(User candidate, Activity activity) {
        List<String> candidateSports = split(candidate.getSportPreferences());
        List<String> candidateSlots = split(candidate.getAvailableTimeSlots());

        int sportScore = candidateSports.stream()
                .anyMatch(sport -> sport.equalsIgnoreCase(activity.getSportType())) ? 35 : 0;
        boolean timeMatched = candidateSlots.stream()
                .anyMatch(slot -> slot.equalsIgnoreCase(timeSlot(activity.getStartTime())))
                || (isWeekend(activity.getStartTime())
                && candidateSlots.stream().anyMatch(slot -> slot.equalsIgnoreCase("WEEKEND")));
        int timeScore = timeMatched ? 25 : 0;
        double distance = distanceKm(
                candidate.getLatitude(),
                candidate.getLongitude(),
                activity.getLatitude(),
                activity.getLongitude()
        );
        int distanceScore = distance <= 1 ? 20
                : distance <= 3 ? 17
                : distance <= 5 ? 14
                : distance <= 10 ? 8 : 0;
        int levelGap = Math.abs(candidate.getSkillLevel().ordinal()
                - activity.getRequiredSkillLevel().ordinal());
        int skillScore = levelGap == 0 ? 10 : levelGap == 1 ? 6 : 2;
        int creditScore = Math.min(10, candidate.getCreditScore() / 10);
        int total = sportScore + timeScore + distanceScore + skillScore + creditScore;

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("sport", sportScore);
        breakdown.put("time", timeScore);
        breakdown.put("distance", distanceScore);
        breakdown.put("skill", skillScore);
        breakdown.put("credit", creditScore);

        List<String> highlights = new ArrayList<>();
        if (sportScore > 0) {
            highlights.add("项目偏好一致");
        }
        if (timeScore > 0) {
            highlights.add("可用时间匹配");
        }
        if (distance <= 3) {
            highlights.add("距离很近");
        }
        if (levelGap == 0) {
            highlights.add("水平相符");
        }
        if (candidate.getCreditScore() >= 90) {
            highlights.add("信用优秀");
        }

        return new CandidateMatchResponse(
                candidate.getId(),
                candidate.getDisplayName(),
                candidate.getSkillLevel(),
                candidate.getCampusZone(),
                candidate.getCreditScore(),
                Math.round(distance * 10.0) / 10.0,
                total,
                breakdown,
                highlights
        );
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
    }

    private String timeSlot(LocalDateTime time) {
        int hour = time.getHour();
        if (hour < 12) {
            return "MORNING";
        }
        if (hour < 18) {
            return "AFTERNOON";
        }
        return "EVENING";
    }

    private boolean isWeekend(LocalDateTime time) {
        return time.getDayOfWeek() == DayOfWeek.SATURDAY
                || time.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private double distanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 999;
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
