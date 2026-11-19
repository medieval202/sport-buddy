package com.campus.sportsbuddy.controller;

import com.campus.sportsbuddy.dto.RealtimeMatchRequest;
import com.campus.sportsbuddy.dto.RealtimeMatchResponse;
import com.campus.sportsbuddy.dto.RealtimeMatchStatusResponse;
import com.campus.sportsbuddy.service.RealtimeMatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeMatchController {

    private final RealtimeMatchService realtimeMatchService;

    public RealtimeMatchController(RealtimeMatchService realtimeMatchService) {
        this.realtimeMatchService = realtimeMatchService;
    }

    @GetMapping("/status")
    public RealtimeMatchStatusResponse status() {
        return realtimeMatchService.status();
    }

    @PostMapping("/matches")
    public RealtimeMatchResponse start(@Valid @RequestBody RealtimeMatchRequest request) {
        return realtimeMatchService.start(request);
    }

    @DeleteMapping("/matches/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel() {
        realtimeMatchService.cancelCurrent();
    }
}
