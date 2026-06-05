package com.alenwifidata.core.config;

import com.alenwifidata.core.websocket.DashboardWebSocketHandler;
import com.alenwifidata.core.websocket.DashboardHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DashboardWebSocketHandler dashboardHandler;
    private final DashboardHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dashboardHandler, "/ws/dashboard")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
