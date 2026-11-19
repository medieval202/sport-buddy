package com.campus.sportsbuddy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class PrototypeFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void servesWebApplication() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Campus Sports Buddy")))
                .andExpect(content().string(containsString("radar-form")));
    }

    @Test
    void loginAndCalculateMatches() throws Exception {
        String token = login("demo", "demo123");

        MvcResult listResult = mockMvc.perform(get("/api/activities")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andReturn();

        long activityId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get(0).get("id").asLong();

        mockMvc.perform(get("/api/activities/{id}/matches", activityId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchScore", greaterThan(0)));
    }

    @Test
    void activitySubmissionReviewAndJoinFlow() throws Exception {
        String studentToken = login("demo", "demo123");
        String adminToken = login("admin", "admin123");
        String otherStudentToken = login("xiaoyu", "xiaoyu123");

        LocalDateTime start = LocalDateTime.now().plusDays(6)
                .withHour(18).withMinute(0).withSecond(0).withNano(0);
        String request = objectMapper.writeValueAsString(new ActivityPayload(
                "测试羽毛球组队",
                "羽毛球",
                "自动化测试活动",
                "东区",
                "东区球馆",
                start,
                start.plusHours(2),
                6,
                "INTERMEDIATE",
                60,
                31.2310,
                121.4730
        ));

        MvcResult createResult = mockMvc.perform(post("/api/activities")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long activityId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(patch("/api/admin/activities/{id}/review", activityId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approved":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/activities/{id}/join", activityId)
                        .header("Authorization", bearer(otherStudentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.joined").value(true));
    }

    @Test
    void reservationRejectsOverlappingTimeWindow() throws Exception {
        String token = login("demo", "demo123");
        LocalDate date = LocalDate.now().plusDays(7);
        String reservation = objectMapper.writeValueAsString(new ReservationPayload(
                1L,
                date,
                "10:00:00",
                "11:00:00"
        ));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservation))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "venueId": 1,
                                  "reservationDate": "%s",
                                  "startTime": "10:30:00",
                                  "endTime": "11:30:00"
                                }
                                """.formatted(date)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该时段已被预约，请选择其他时间"));
    }

    @Test
    void studentCannotAccessAdminApi() throws Exception {
        String token = login("demo", "demo123");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void twoOnlineStudentsAreMatchedInRealTime() throws Exception {
        String demoToken = login("demo", "demo123");
        String xiaoyuToken = login("xiaoyu", "xiaoyu123");
        String payload = """
                {
                  "sportType": "网球",
                  "timeWindow": "EVENING",
                  "campusZone": "东区",
                  "skillLevel": "INTERMEDIATE",
                  "latitude": 31.2310,
                  "longitude": 121.4730
                }
                """;

        mockMvc.perform(post("/api/realtime/matches")
                        .header("Authorization", bearer(demoToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEARCHING"));

        mockMvc.perform(post("/api/realtime/matches")
                        .header("Authorization", bearer(xiaoyuToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.partner.displayName").value("陈同学"))
                .andExpect(jsonPath("$.matchScore", greaterThan(0)));

        mockMvc.perform(get("/api/realtime/status")
                        .header("Authorization", bearer(demoToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.status").value("MATCHED"))
                .andExpect(jsonPath("$.current.partner.displayName").value("小雨"));

        mockMvc.perform(delete("/api/realtime/matches/current")
                        .header("Authorization", bearer(xiaoyuToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/realtime/status")
                        .header("Authorization", bearer(demoToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.status").value("SEARCHING"));

        mockMvc.perform(delete("/api/realtime/matches/current")
                        .header("Authorization", bearer(demoToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void radarSessionScansAndCreatesMatchInvitation() throws Exception {
        String token = login("demo", "demo123");
        MvcResult startResult = mockMvc.perform(post("/api/radar/sessions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "radiusKm": 5.0,
                                  "topN": 5,
                                  "simulationMode": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.sectors").value(8))
                .andExpect(jsonPath("$.blips[0].userId").isNumber())
                .andReturn();

        JsonNode started = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String sessionId = started.get("sessionId").asText();
        long candidateId = started.get("blips").get(0).get("userId").asLong();

        mockMvc.perform(post("/api/radar/sessions/{sessionId}/candidates/{candidateId}/lock",
                                sessionId, candidateId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.selectedCandidateId").value(candidateId));

        mockMvc.perform(post("/api/radar/sessions/{sessionId}/candidates/{candidateId}/engage",
                                sessionId, candidateId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.matchId").isNumber())
                .andExpect(jsonPath("$.candidateId").value(candidateId));
    }

    private String login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(username, password));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record LoginPayload(String username, String password) {
    }

    private record ActivityPayload(
            String title,
            String sportType,
            String description,
            String campusZone,
            String locationName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int maxParticipants,
            String requiredSkillLevel,
            int minCreditScore,
            double latitude,
            double longitude
    ) {
    }

    private record ReservationPayload(
            Long venueId,
            LocalDate reservationDate,
            String startTime,
            String endTime
    ) {
    }
}
