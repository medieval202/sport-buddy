package com.campus.sportsbuddy.controller;

import com.campus.sportsbuddy.dto.ActivityRequest;
import com.campus.sportsbuddy.dto.ActivityResponse;
import com.campus.sportsbuddy.dto.CandidateMatchResponse;
import com.campus.sportsbuddy.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public List<ActivityResponse> list(
            @RequestParam(required = false) String sport
    ) {
        return activityService.listApproved(sport);
    }

    @GetMapping("/mine")
    public List<ActivityResponse> mine() {
        return activityService.listMine();
    }

    @PostMapping
    public ActivityResponse create(@Valid @RequestBody ActivityRequest request) {
        return activityService.create(request);
    }

    @PostMapping("/{id}/join")
    public ActivityResponse join(@PathVariable Long id) {
        return activityService.join(id);
    }

    @DeleteMapping("/{id}/join")
    public ActivityResponse leave(@PathVariable Long id) {
        return activityService.leave(id);
    }

    @GetMapping("/{id}/matches")
    public List<CandidateMatchResponse> matches(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return activityService.match(id, limit);
    }

    @PostMapping("/{id}/invite/{userId}")
    public Map<String, String> invite(@PathVariable Long id, @PathVariable Long userId) {
        activityService.invite(id, userId);
        return Map.of("message", "邀请通知已发送");
    }
}
