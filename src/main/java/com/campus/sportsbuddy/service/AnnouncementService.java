package com.campus.sportsbuddy.service;

import com.campus.sportsbuddy.dto.AnnouncementRequest;
import com.campus.sportsbuddy.dto.AnnouncementResponse;
import com.campus.sportsbuddy.model.Announcement;
import com.campus.sportsbuddy.repository.AnnouncementRepository;
import com.campus.sportsbuddy.websocket.UserNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserService userService;
    private final UserNotifier userNotifier;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            UserService userService,
            UserNotifier userNotifier
    ) {
        this.announcementRepository = announcementRepository;
        this.userService = userService;
        this.userNotifier = userNotifier;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> list() {
        return announcementRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    @Transactional
    public AnnouncementResponse publish(AnnouncementRequest request) {
        Announcement announcement = new Announcement();
        announcement.setTitle(request.title().trim());
        announcement.setContent(request.content().trim());
        announcement.setCreatedBy(userService.currentUser());
        Announcement saved = announcementRepository.save(announcement);
        userNotifier.broadcast(
                "ANNOUNCEMENT",
                "校园运动公告：" + saved.getTitle(),
                Map.of("announcementId", saved.getId())
        );
        return AnnouncementResponse.from(saved);
    }
}
