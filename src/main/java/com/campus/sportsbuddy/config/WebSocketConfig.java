package com.campus.sportsbuddy.config;

import com.campus.sportsbuddy.websocket.NotificationHandshakeInterceptor;
import com.campus.sportsbuddy.websocket.UserNotifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UserNotifier userNotifier;
    private final NotificationHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(
            UserNotifier userNotifier,
            NotificationHandshakeInterceptor handshakeInterceptor
    ) {
        this.userNotifier = userNotifier;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userNotifier, "/ws/notifications")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
