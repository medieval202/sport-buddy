package com.campus.sportsbuddy.dto;

public record RealtimeMatchStatusResponse(
        int onlineUsers,
        int searchingUsers,
        RealtimeMatchResponse current
) {
}
