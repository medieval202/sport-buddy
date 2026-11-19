package com.campus.sportsbuddy.controller;

import com.campus.sportsbuddy.dto.ActivityResponse;
import com.campus.sportsbuddy.dto.AdminReviewRequest;
import com.campus.sportsbuddy.dto.AnnouncementRequest;
import com.campus.sportsbuddy.dto.AnnouncementResponse;
import com.campus.sportsbuddy.dto.CreditUpdateRequest;
import com.campus.sportsbuddy.dto.UserResponse;
import com.campus.sportsbuddy.service.ActivityService;
import com.campus.sportsbuddy.service.AdminService;
import com.campus.sportsbuddy.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ActivityService activityService;
    private final AdminService adminService;
    private final AnnouncementService announcementService;

    public AdminController(
            ActivityService activityService,
            AdminService adminService,
            AnnouncementService announcementService
    ) {
        this.activityService = activityService;
        this.adminService = adminService;
        this.announcementService = announcementService;
    }

    @GetMapping("/activities/pending")
    public List<ActivityResponse> pendingActivities() {
        return activityService.pending();
    }

    @PatchMapping("/activities/{id}/review")
    public ActivityResponse review(
            @PathVariable Long id,
            @Valid @RequestBody AdminReviewRequest request
    ) {
        return activityService.review(id, request);
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return adminService.students();
    }

    @PatchMapping("/users/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody CreditUpdateRequest request
    ) {
        return adminService.updateStudent(id, request);
    }

    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse publish(
            @Valid @RequestBody AnnouncementRequest request
    ) {
        return announcementService.publish(request);
    }
}
