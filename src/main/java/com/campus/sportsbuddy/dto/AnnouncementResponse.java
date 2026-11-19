package com.campus.sportsbuddy.dto;

import com.campus.sportsbuddy.model.Announcement;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        String title,
        String content,
        String authorName,
        LocalDateTime createdAt
) {
    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getCreatedBy().getDisplayName(),
                announcement.getCreatedAt()
        );
    }
}
