package com.campus.sportsbuddy.service;

import com.campus.sportsbuddy.dto.RadarBlipResponse;
import com.campus.sportsbuddy.dto.RadarMatchResponse;
import com.campus.sportsbuddy.dto.RadarSessionResponse;
import com.campus.sportsbuddy.dto.RadarStartRequest;
import com.campus.sportsbuddy.model.RadarMatch;
import com.campus.sportsbuddy.model.RadarMatchStatus;
import com.campus.sportsbuddy.model.RadarSessionState;
import com.campus.sportsbuddy.model.Role;
import com.campus.sportsbuddy.model.User;
import com.campus.sportsbuddy.repository.RadarMatchRepository;
import com.campus.sportsbuddy.repository.UserRepository;
import com.campus.sportsbuddy.security.SecurityUtils;
import com.campus.sportsbuddy.websocket.UserNotifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RadarMatchingService {

    private static final int SECTORS = 8;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int MIN_RADAR_CREDIT = 60;

    private final UserRepository userRepository;
    private final RadarMatchRepository radarMatchRepository;
    private final RealtimePresenceService presenceService;
    private final UserNotifier userNotifier;
    private final Map<String, RadarSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, String> sessionByUser = new ConcurrentHashMap<>();
    private final Object sessionLock = new Object();

    public RadarMatchingService(
            UserRepository userRepository,
            RadarMatchRepository radarMatchRepository,
            RealtimePresenceService presenceService,
            UserNotifier userNotifier
    ) {
        this.userRepository = userRepository;
        this.radarMatchRepository = radarMatchRepository;
        this.presenceService = presenceService;
        this.userNotifier = userNotifier;
    }

    @Transactional(readOnly = true)
    public RadarSessionResponse start(RadarStartRequest request) {
        User current = currentUser();
        presenceService.markActive(current.getId());
        synchronized (sessionLock) {
            String previousId = sessionByUser.remove(current.getId());
            if (previousId != null) {
                sessions.remove(previousId);
            }
            RadarSession session = new RadarSession(UUID.randomUUID().toString(), current, request);
            sessions.put(session.id, session);
            sessionByUser.put(current.getId(), session.id);
            RadarSessionResponse response = refresh(session, "RADAR_READY", true);
            userNotifier.sendToUser(
                    current.getId(),
                    "RADAR_READY",
                    "雷达匹配已启动，正在扫描 8 个方位扇区",
                    response
            );
            return response;
        }
    }

    @Transactional(readOnly = true)
    public RadarSessionResponse current() {
        User current = currentUser();
        String sessionId = sessionByUser.get(current.getId());
        if (sessionId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前没有雷达匹配会话");
        }
        RadarSession session = sessions.get(sessionId);
        if (session == null) {
            sessionByUser.remove(current.getId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "雷达匹配会话已结束");
        }
        synchronized (session) {
            return snapshot(session);
        }
    }

    @Transactional(readOnly = true)
    public RadarSessionResponse lockCandidate(String sessionId, Long candidateId) {
        RadarSession session = ownedSession(sessionId);
        synchronized (session) {
            RadarBlipResponse candidate = session.latestBlips.stream()
                    .filter(blip -> blip.userId().equals(candidateId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "候选已不在当前 Top-N 中"
                    ));
            session.state = RadarSessionState.PAUSED;
            session.selectedCandidateId = candidate.userId();
            session.lastEvent = "RADAR_LOCKED";
            session.updatedAt = LocalDateTime.now();
            RadarSessionResponse response = snapshot(session);
            userNotifier.sendToUser(
                    session.owner.getId(),
                    "RADAR_LOCKED",
                    "已锁定 " + candidate.displayName() + "，扫描暂停",
                    response
            );
            return response;
        }
    }

    @Transactional(readOnly = true)
    public RadarSessionResponse resume(String sessionId) {
        RadarSession session = ownedSession(sessionId);
        synchronized (session) {
            if (session.state == RadarSessionState.CLOSED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "会话已关闭");
            }
            session.state = RadarSessionState.RUNNING;
            session.selectedCandidateId = null;
            session.lastEvent = "RADAR_RESUMED";
            session.updatedAt = LocalDateTime.now();
            RadarSessionResponse response = refresh(session, "RADAR_RESUMED", false);
            userNotifier.sendToUser(
                    session.owner.getId(),
                    "RADAR_RESUMED",
                    "雷达扫描已恢复",
                    response
            );
            return response;
        }
    }

    @Transactional
    public RadarMatchResponse engage(String sessionId, Long candidateId) {
        RadarSession session = ownedSession(sessionId);
        RadarBlipResponse blip;
        synchronized (session) {
            blip = session.latestBlips.stream()
                    .filter(item -> item.userId().equals(candidateId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "候选已不在当前 Top-N 中"
                    ));
            session.selectedCandidateId = candidateId;
            session.state = RadarSessionState.CLOSED;
            session.lastEvent = "RADAR_CLOSED";
            session.updatedAt = LocalDateTime.now();
        }

        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "候选用户不存在"));
        RadarMatch match = new RadarMatch();
        match.setRequester(session.owner);
        match.setCandidate(candidate);
        match.setSportType(split(session.owner.getSportPreferences()).stream().findFirst().orElse("运动"));
        match.setMatchScore(blip.score());
        match.setStatus(RadarMatchStatus.PENDING);
        match = radarMatchRepository.save(match);

        RadarMatchResponse response = new RadarMatchResponse(
                match.getId(),
                match.getStatus(),
                match.getSportType(),
                match.getMatchScore(),
                session.owner.getId(),
                session.owner.getDisplayName(),
                candidate.getId(),
                candidate.getDisplayName(),
                match.getCreatedAt(),
                "组队邀约已发送，等待对方确认"
        );
        userNotifier.sendToUser(
                candidateId,
                "RADAR_INVITATION",
                session.owner.getDisplayName() + " 通过雷达匹配向你发起运动邀约",
                response
        );
        userNotifier.sendToUser(
                session.owner.getId(),
                "RADAR_CLOSED",
                "雷达已关闭，组队邀约已发送",
                response
        );
        closeSession(session);
        return response;
    }

    @Transactional
    public RadarMatchResponse respond(Long matchId, boolean accept) {
        User current = currentUser();
        RadarMatch match = radarMatchRepository.findDetailById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "邀约不存在"));
        if (!match.getCandidate().getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权处理该邀约");
        }
        if (match.getStatus() != RadarMatchStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该邀约已经处理");
        }
        match.setStatus(accept ? RadarMatchStatus.ACCEPTED : RadarMatchStatus.REJECTED);
        match.setRespondedAt(LocalDateTime.now());
        radarMatchRepository.save(match);
        RadarMatchResponse response = toMatchResponse(
                match,
                accept ? "对方已接受组队邀约" : "对方拒绝了组队邀约"
        );
        userNotifier.sendToUser(
                match.getRequester().getId(),
                accept ? "RADAR_MATCH_ACCEPTED" : "RADAR_MATCH_REJECTED",
                response.message(),
                response
        );
        return response;
    }

    @Transactional(readOnly = true)
    public RadarSessionResponse simulateCandidateEvent(String sessionId) {
        RadarSession session = ownedSession(sessionId);
        synchronized (session) {
            RadarBlipResponse removed = session.latestBlips.stream()
                    .filter(blip -> !session.blockedUserIds.contains(blip.userId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "没有可模拟失联的候选"
                    ));
            session.blockedUserIds.add(removed.userId());
            session.currentSector = (session.currentSector + 1) % SECTORS;
            session.scannedSectors = Math.min(SECTORS, session.scannedSectors + 1);
            refresh(
                    session,
                    removed.displayName() + " 已临时下线，Top-N 自动补位",
                    false
            );
            session.lastEvent = removed.displayName() + " 已临时下线，Top-N 自动补位";
            RadarSessionResponse response = snapshot(session);
            userNotifier.sendToUser(
                    session.owner.getId(),
                    "RADAR_EVENT",
                    session.lastEvent,
                    response
            );
            return response;
        }
    }

    public void close(String sessionId) {
        RadarSession session = ownedSession(sessionId);
        session.state = RadarSessionState.CLOSED;
        session.lastEvent = "RADAR_CLOSED";
        session.updatedAt = LocalDateTime.now();
        userNotifier.sendToUser(
                session.owner.getId(),
                "RADAR_CLOSED",
                "雷达匹配已关闭",
                snapshot(session)
        );
        closeSession(session);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void tickAll() {
        LocalDateTime now = LocalDateTime.now();
        sessions.values().forEach(session -> {
            if (session.expiresAt.isBefore(now)) {
                closeSession(session);
                return;
            }
            if (session.state != RadarSessionState.RUNNING) {
                return;
            }
            synchronized (session) {
                session.currentSector = (session.currentSector + 1) % SECTORS;
                session.scannedSectors = Math.min(SECTORS, session.scannedSectors + 1);
                RadarSessionResponse response = refresh(session, "SECTOR_SWEEP", false);
                userNotifier.sendToUser(
                        session.owner.getId(),
                        "RADAR_TICK",
                        "雷达扫描更新",
                        response
                );
            }
        });
    }

    private RadarSessionResponse refresh(RadarSession session, String event, boolean initial) {
        List<RadarBlipResponse> candidates = calculateCandidates(session);
        Map<Long, Integer> previous = session.latestBlips.stream()
                .collect(Collectors.toMap(RadarBlipResponse::userId, RadarBlipResponse::score));
        Map<Long, Integer> next = candidates.stream()
                .collect(Collectors.toMap(RadarBlipResponse::userId, RadarBlipResponse::score));
        boolean changed = !previous.keySet().equals(next.keySet())
                || previous.entrySet().stream().anyMatch(entry ->
                next.containsKey(entry.getKey())
                        && Math.abs(next.get(entry.getKey()) - entry.getValue()) >= 5);
        if (initial || changed) {
            session.version++;
        }
        session.latestBlips = candidates;
        session.onlineCandidates = candidates.size();
        session.lastEvent = changed && !initial ? "TOP_N_UPDATED" : event;
        session.updatedAt = LocalDateTime.now();
        return snapshot(session);
    }

    private List<RadarBlipResponse> calculateCandidates(RadarSession session) {
        User owner = session.owner;
        List<User> users = userRepository.findByRoleOrderByCreatedAtDesc(Role.STUDENT);
        if (Boolean.TRUE.equals(session.request.simulationMode())) {
            users.forEach(user -> {
                if (!user.getId().equals(owner.getId())) {
                    presenceService.markActive(user.getId());
                }
            });
        }

        List<User> eligibleUsers = users.stream()
                .filter(User::isEnabled)
                .filter(user -> !user.getId().equals(owner.getId()))
                .filter(user -> !session.blockedUserIds.contains(user.getId()))
                .filter(user -> Boolean.TRUE.equals(session.request.simulationMode())
                        || presenceService.isOnline(user.getId()))
                .toList();
        List<RadarBlipResponse> strictMatches = eligibleUsers.stream()
                .map(user -> scoreCandidate(session, user, false))
                .filter(item -> item != null)
                .sorted(Comparator.comparingInt(RadarBlipResponse::score).reversed())
                .limit(session.request.topN())
                .toList();
        if (!strictMatches.isEmpty() || !Boolean.TRUE.equals(session.request.simulationMode())) {
            return strictMatches;
        }
        return eligibleUsers.stream()
                .map(user -> scoreCandidate(session, user, true))
                .filter(item -> item != null)
                .sorted(Comparator.comparingInt(RadarBlipResponse::score).reversed())
                .limit(session.request.topN())
                .toList();
    }

    private RadarBlipResponse scoreCandidate(
            RadarSession session,
            User candidate,
            boolean relaxedSimulation
    ) {
        User owner = session.owner;
        Set<String> requestedSports = normalizeSports(split(owner.getSportPreferences()));
        Set<String> candidateSports = normalizeSports(split(candidate.getSportPreferences()));
        if (!relaxedSimulation && candidateSports.stream().noneMatch(requestedSports::contains)) {
            return null;
        }
        Set<String> requestedTime = normalizeSports(split(owner.getAvailableTimeSlots()));
        Set<String> candidateTime = normalizeSports(split(candidate.getAvailableTimeSlots()));
        if (!relaxedSimulation && candidateTime.stream().noneMatch(requestedTime::contains)) {
            return null;
        }
        if (candidate.getCreditScore() < MIN_RADAR_CREDIT) {
            return null;
        }

        double distance = distanceKm(
                owner.getLatitude(),
                owner.getLongitude(),
                candidate.getLatitude(),
                candidate.getLongitude()
        );
        if (distance > session.request.radiusKm() * 1.25) {
            return null;
        }

        double sportSimilarity = jaccard(requestedSports, candidateSports);
        double timeSimilarity = jaccard(requestedTime, candidateTime);
        double distanceSimilarity = Math.max(0, 1 - distance / session.request.radiusKm());
        int levelGap = Math.abs(owner.getSkillLevel().ordinal() - candidate.getSkillLevel().ordinal());
        double skillSimilarity = 1 - levelGap / 2.0;
        double creditSimilarity = candidate.getCreditScore() / 100.0;

        double[] weights = {0.35, 0.25, 0.20, 0.10, 0.10};
        double[] similarities = {sportSimilarity, timeSimilarity, distanceSimilarity, skillSimilarity, creditSimilarity};
        String[] keys = {"sport", "time", "distance", "skill", "credit"};
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        double total = 0;
        for (int i = 0; i < keys.length; i++) {
            int points = (int)Math.round(100 * weights[i] * similarities[i]);
            breakdown.put(keys[i], points);
            total += points;
        }

        double angle = bearing(
                owner.getLatitude(), owner.getLongitude(),
                candidate.getLatitude(), candidate.getLongitude()
        );
        int sector = Math.min(SECTORS - 1, (int)(angle / (360.0 / SECTORS)));
        List<String> highlights = new ArrayList<>();
        if (sportSimilarity > 0) {
            highlights.add("项目匹配");
        }
        if (timeSimilarity > 0) {
            highlights.add("时间匹配");
        }
        if (distance <= 3) {
            highlights.add("距离较近");
        }
        if (levelGap == 0) {
            highlights.add("水平相符");
        }
        if (candidate.getCreditScore() >= 90) {
            highlights.add("信用优秀");
        }

        return new RadarBlipResponse(
                candidate.getId(),
                candidate.getDisplayName(),
                candidate.getSkillLevel(),
                candidate.getCampusZone(),
                candidate.getCreditScore(),
                Math.round(distance * 100.0) / 100.0,
                sector,
                Math.round(angle * 10.0) / 10.0,
                Math.min(1.0, Math.round(distance / session.request.radiusKm() * 100.0) / 100.0),
                Math.min(100, (int)Math.round(total)),
                breakdown,
                highlights,
                true
        );
    }

    private RadarSessionResponse snapshot(RadarSession session) {
        return new RadarSessionResponse(
                session.id,
                session.state.name(),
                session.version,
                session.currentSector,
                SECTORS,
                session.scannedSectors,
                session.onlineCandidates,
                session.request.radiusKm(),
                session.selectedCandidateId,
                session.lastEvent,
                List.copyOf(session.latestBlips),
                session.updatedAt
        );
    }

    private RadarSession ownedSession(String sessionId) {
        User current = currentUser();
        RadarSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "雷达会话不存在");
        }
        if (!session.owner.getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该雷达会话");
        }
        return session;
    }

    private void closeSession(RadarSession session) {
        sessions.remove(session.id);
        sessionByUser.remove(session.owner.getId(), session.id);
    }

    private User currentUser() {
        return userRepository.findByUsername(SecurityUtils.currentUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    private RadarMatchResponse toMatchResponse(RadarMatch match, String message) {
        return new RadarMatchResponse(
                match.getId(),
                match.getStatus(),
                match.getSportType(),
                match.getMatchScore(),
                match.getRequester().getId(),
                match.getRequester().getDisplayName(),
                match.getCandidate().getId(),
                match.getCandidate().getDisplayName(),
                match.getCreatedAt(),
                message
        );
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return intersection.size() / (double)union.size();
    }

    private Set<String> normalizeSports(Collection<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private double distanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double bearing(Double lat1, Double lon1, Double lat2, Double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLon);
        double degrees = Math.toDegrees(Math.atan2(y, x));
        return (degrees + 360) % 360;
    }

    private static final class RadarSession {
        private final String id;
        private final User owner;
        private final RadarStartRequest request;
        private final Set<Long> blockedUserIds = ConcurrentHashMap.newKeySet();
        private RadarSessionState state = RadarSessionState.RUNNING;
        private int version;
        private int currentSector;
        private int scannedSectors = 1;
        private int onlineCandidates;
        private Long selectedCandidateId;
        private String lastEvent = "RADAR_READY";
        private List<RadarBlipResponse> latestBlips = List.of();
        private LocalDateTime updatedAt = LocalDateTime.now();
        private final LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        private RadarSession(String id, User owner, RadarStartRequest request) {
            this.id = id;
            this.owner = owner;
            this.request = request;
        }
    }
}
