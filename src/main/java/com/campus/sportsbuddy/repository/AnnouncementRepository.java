package com.campus.sportsbuddy.repository;

import com.campus.sportsbuddy.model.Announcement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @EntityGraph(attributePaths = "createdBy")
    List<Announcement> findTop20ByOrderByCreatedAtDesc();
}
