package com.campus.sportsbuddy.service;

import com.campus.sportsbuddy.dto.RealtimeMatchRequest;
import com.campus.sportsbuddy.dto.RealtimeMatchResponse;
import com.campus.sportsbuddy.dto.RealtimeMatchStatusResponse;
import com.campus.sportsbuddy.dto.RealtimePartnerResponse;
import com.campus.sportsbuddy.model.MatchTimeWindow;
import com.campus.sportsbuddy.model.SkillLevel;
import com.campus.sportsbuddy.model.User;
import com.campus.sportsbuddy.repository.UserRepository;
import com.campus.sportsbuddy.security.SecurityUtils;
import com.campus.sportsbuddy.websocket.UserNotifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealtimeMatchService {

    private static final int MIN_MATCH_SCORE = 55;
    private static final int INTENT_MINUTES = 30;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final UserRepository userRepository;
    private final RealtimePresenceService presenceService;
    private final UserNotifier userNotifier;
    private final Map<Long, MatchIntent> intentsByUser = new ConcurrentHashMap<>();
    private final Object matchLock = new Object();

    public RealtimeMatchService(
            UserRepository userRepository,
            RealtimePresenceService presenceService,
            UserNotifier userNotifier
    ) {
        this.userRepository = userRepository;
        this.presenceService = presenceService;
        this.userNotifier = userNotifier;
    }

    @Transactional(readOnly = true)
    public RealtimeMatchStatusResponse status() {
        User current = currentUser();
        presenceService.markActive(current.getId());
        cleanupExpired();
        RealtimeMatchResponse currentMatch = responseFor(intentsByUser.get(current.getId()));
        return new RealtimeMatchStatusResponse(
                presenceService.onlineCount(),
                searchingCount(),
                currentMatch
        );
    }

    @Transactional(readOnly = true)
    public RealtimeMatchResponse start(RealtimeMatchRequest request) {
        User current = currentUser();
        presenceService.markActive(current.getId());
        cleanupExpired();

        synchronized (matchLock) {
            intentsByUser.remove(current.getId());

            Candidate candidate = intentsByUser.values().stream()
                    .filter(intent -> intent.status == MatchStatus.SEARCHING)
                    .filter(intent -> !intent.user.getId().equals(current.getId()))
                    .filter(intent -> !intent.expiresAt.isBefore(LocalDateTime.now()))
                    .filter(intent -> presenceService.isOnline(intent.user.getId()))
                    .filter(intent -> intent.request.sportType().equalsIgnoreCase(request.sportType().trim()))
                    .filter(intent -> intent.request.timeWindow() == request.timeWindow())
                    .map(intent -> score(intent, request, current))
                    .filter(scored -> scored.score() >= MIN_MATCH_SCORE)
                    .max(Comparator.comparingInt(Candidate::score))
                    .orElse(null);

            MatchIntent mine = new MatchIntent(current, normalize(request));
            if (candidate == null) {
                intentsByUser.put(current.getId(), mine);
                return responseFor(mine);
            }

            String matchId = UUID.randomUUID().toString();
            MatchIntent partnerIntent = candidate.intent();
            mine.status = MatchStatus.MATCHED;
            mine.matchId = matchId;
            mine.partnerUserId = partnerIntent.user.getId();
            mine.matchScore = candidate.score();
            mine.scoreBreakdown = candidate.breakdown();
            mine.highlights = candidate.highlights();

            partnerIntent.status = MatchStatus.MATCHED;
            partnerIntent.matchId = matchId;
            partnerIntent.partnerUserId = current.getId();
            partnerIntent.matchScore = candidate.score();
            partnerIntent.scoreBreakdown = candidate.breakdown();
            partnerIntent.highlights = candidate.highlights();

            intentsByUser.put(current.getId(), mine);
            intentsByUser.put(partnerIntent.user.getId(), partnerIntent);

            RealtimeMatchResponse mineResponse = responseFor(mine);
            RealtimeMatchResponse partnerResponse = responseFor(partnerIntent);
            userNotifier.sendToUser(
                    partnerIntent.user.getId(),
                    "REALTIME_MATCH_FOUND",
                    "实时匹配成功：找到 " + current.getDisplayName() + "，匹配度 " + candidate.score() + " 分",
                    partnerResponse
            );
            userNotifier.sendToUser(
                    current.getId(),
                    "REALTIME_MATCH_FOUND",
                    "实时匹配成功：找到 " + partnerIntent.user.getDisplayName() + "，匹配度 " + candidate.score() + " 分",
                    mineResponse
            );
            return mineResponse;
        }
    }

    @Transactional(readOnly = true)
    public void cancelCurrent() {
        User current = currentUser();
        presenceService.markActive(current.getId());
        synchronized (matchLock) {
            MatchIntent mine = intentsByUser.remove(current.getId());
            if (mine == null || mine.status != MatchStatus.MATCHED || mine.matchId == null) {
                return;
            }
            intentsByUser.values().stream()
                    .filter(intent -> mine.matchId.equals(intent.matchId))
                    .findFirst()
                    .ifPresent(partner -> {
                        partner.status = MatchStatus.SEARCHING;
                        partner.matchId = null;
                        partner.partnerUserId = null;
                        partner.matchScore = null;
                        partner.scoreBreakdown = Map.of();
                        partner.highlights = List.of();
                        partner.createdAt = LocalDateTime.now();
                        partner.expiresAt = partner.createdAt.plusMinutes(INTENT_MINUTES);
                        userNotifier.sendToUser(
                                partner.user.getId(),
                                "REALTIME_MATCH_CANCELLED",
                                "对方取消了本次匹配，系统正在继续为你寻找搭子",
                                Map.of("status", "SEARCHING")
                        );
                    });
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void pushSearchingStatus() {
        cleanupExpired();
        int onlineUsers = presenceService.onlineCount();
        LocalDateTime now = LocalDateTime.now();
        int validSearching = 0;
        for (MatchIntent intent : intentsByUser.values()) {
            if (intent.status == MatchStatus.SEARCHING
                    && !intent.expiresAt.isBefore(now)
                    && presenceService.isOnline(intent.user.getId())) {
                validSearching++;
            }
        }

        int finalSearching = validSearching;
        int finalOnline = onlineUsers;
        for (MatchIntent intent : intentsByUser.values()) {
            if (intent.status == MatchStatus.SEARCHING
                    && !intent.expiresAt.isBefore(now)
                    && presenceService.isOnline(intent.user.getId())) {
                long waitingSeconds = Math.max(
                        0,
                        Duration.between(intent.createdAt, now).getSeconds()
                );
                userNotifier.sendToUser(
                        intent.user.getId(),
                        "REALTIME_SEARCH_TICK",
                        "实时匹配仍在进行",
                        Map.of(
                                "onlineUsers", finalOnline,
                                "searchingUsers", finalSearching,
                                "waitingSeconds", waitingSeconds
                        )
                );
            }
        }
    }

    private Candidate score(MatchIntent partnerIntent, RealtimeMatchRequest request, User current) {
        int sport = request.sportType().trim().equalsIgnoreCase(partnerIntent.request.sportType()) ? 35 : 0;
        int time = request.timeWindow() == partnerIntent.request.timeWindow() ? 25 : 0;
        double distance = distanceKm(
                request.latitude(), request.longitude(),
                partnerIntent.request.latitude(), partnerIntent.request.longitude()
        );
        int distanceScore = distance <= 1 ? 20
                : distance <= 3 ? 17
                : distance <= 5 ? 14
                : distance <= 10 ? 8 : 0;
        int levelGap = Math.abs(request.skillLevel().ordinal() - partnerIntent.request.skillLevel().ordinal());
        int skill = levelGap == 0 ? 10 : levelGap == 1 ? 6 : 2;
        int credit = Math.min(10, partnerIntent.user.getCreditScore() / 10);
        int total = sport + time + distanceScore + skill + credit;

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("sport", sport);
        breakdown.put("time", time);
        breakdown.put("distance", distanceScore);
        breakdown.put("skill", skill);
        breakdown.put("credit", credit);

        List<String> highlights = new ArrayList<>();
        if (sport > 0) {
            highlights.add("项目一致");
        }
        if (time > 0) {
            highlights.add("时间一致");
        }
        if (distance <= 3) {
            highlights.add("距离很近");
        }
        if (levelGap == 0) {
            highlights.add("水平相符");
        }
        if (partnerIntent.user.getCreditScore() >= 90) {
            highlights.add("信用优秀");
        }

        return new Candidate(
                partnerIntent,
                total,
                breakdown,
                highlights,
                Math.round(distance * 10.0) / 10.0,
                current
        );
    }

    private RealtimeMatchResponse responseFor(MatchIntent intent) {
        if (intent == null || intent.status == MatchStatus.CANCELLED
                || (intent.expiresAt != null && intent.expiresAt.isBefore(LocalDateTime.now()))) {
            return RealtimeMatchResponse.none();
        }

        RealtimePartnerResponse partner = null;
        if (intent.status == MatchStatus.MATCHED && intent.partnerUserId != null) {
            User partnerUser = userRepository.findById(intent.partnerUserId).orElse(null);
            if (partnerUser != null) {
                double distance = distanceKm(
                        intent.request.latitude(), intent.request.longitude(),
                        partnerUser.getLatitude(), partnerUser.getLongitude()
                );
                partner = new RealtimePartnerResponse(
                        partnerUser.getId(),
                        partnerUser.getDisplayName(),
                        partnerUser.getSkillLevel(),
                        partnerUser.getCampusZone(),
                        partnerUser.getCreditScore(),
                        Math.round(distance * 10.0) / 10.0,
                        split(partnerUser.getSportPreferences())
                );
            }
        }

        return new RealtimeMatchResponse(
                intent.matchId,
                intent.status.name(),
                intent.request.sportType(),
                intent.request.timeWindow().name(),
                partner,
                intent.matchScore,
                intent.scoreBreakdown,
                intent.highlights,
                intent.createdAt,
                intent.expiresAt
        );
    }

    private int searchingCount() {
        return (int) intentsByUser.values().stream()
                .filter(intent -> intent.status == MatchStatus.SEARCHING)
                .filter(intent -> !intent.expiresAt.isBefore(LocalDateTime.now()))
                .count();
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        intentsByUser.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private RealtimeMatchRequest normalize(RealtimeMatchRequest request) {
        return new RealtimeMatchRequest(
                request.sportType().trim(),
                request.timeWindow(),
                request.campusZone().trim(),
                request.skillLevel(),
                request.latitude(),
                request.longitude()
        );
    }

    private User currentUser() {
        return userRepository.findByUsername(SecurityUtils.currentUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
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

    private enum MatchStatus {
        SEARCHING,
        MATCHED,
        CANCELLED
    }

    private static final class MatchIntent {
        private final User user;
        private final RealtimeMatchRequest request;
        private MatchStatus status = MatchStatus.SEARCHING;
        private String matchId;
        private Long partnerUserId;
        private Integer matchScore;
        private Map<String, Integer> scoreBreakdown = Map.of();
        private List<String> highlights = List.of();
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime expiresAt = createdAt.plusMinutes(INTENT_MINUTES);

        private MatchIntent(User user, RealtimeMatchRequest request) {
            this.user = user;
            this.request = request;
        }
    }

    private record Candidate(
            MatchIntent intent,
            int score,
            Map<String, Integer> breakdown,
            List<String> highlights,
            double distanceKm,
            User current
    ) {
    }
}
