package com.campus.sportsbuddy.controller;

import com.campus.sportsbuddy.dto.RadarMatchResponse;
import com.campus.sportsbuddy.dto.RadarSessionResponse;
import com.campus.sportsbuddy.dto.RadarStartRequest;
import com.campus.sportsbuddy.service.RadarMatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/radar")
public class RadarController {

    private final RadarMatchingService radarMatchingService;

    public RadarController(RadarMatchingService radarMatchingService) {
        this.radarMatchingService = radarMatchingService;
    }

    @PostMapping("/sessions")
    public RadarSessionResponse start(@Valid @RequestBody RadarStartRequest request) {
        return radarMatchingService.start(request);
    }

    @GetMapping("/sessions/current")
    public RadarSessionResponse current() {
        return radarMatchingService.current();
    }

    @PostMapping("/sessions/{sessionId}/candidates/{candidateId}/lock")
    public RadarSessionResponse lock(
            @PathVariable String sessionId,
            @PathVariable Long candidateId
    ) {
        return radarMatchingService.lockCandidate(sessionId, candidateId);
    }

    @PostMapping("/sessions/{sessionId}/resume")
    public RadarSessionResponse resume(@PathVariable String sessionId) {
        return radarMatchingService.resume(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/candidates/{candidateId}/engage")
    public RadarMatchResponse engage(
            @PathVariable String sessionId,
            @PathVariable Long candidateId
    ) {
        return radarMatchingService.engage(sessionId, candidateId);
    }

    @PostMapping("/sessions/{sessionId}/simulate-event")
    public RadarSessionResponse simulateEvent(@PathVariable String sessionId) {
        return radarMatchingService.simulateCandidateEvent(sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable String sessionId) {
        radarMatchingService.close(sessionId);
    }

    @PostMapping("/matches/{matchId}/respond")
    public RadarMatchResponse respond(
            @PathVariable Long matchId,
            @RequestParam boolean accept
    ) {
        return radarMatchingService.respond(matchId, accept);
    }
}
