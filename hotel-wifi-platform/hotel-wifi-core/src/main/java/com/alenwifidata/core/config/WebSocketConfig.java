package com.alenwifidata.core.config;

import com.alenwifidata.core.device.relay.DeviceRelayHandler;
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
    private final DeviceRelayHandler deviceRelayHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Dashboard 实时推送
        registry.addHandler(dashboardHandler, "/ws/dashboard")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");

        // 内网设备连接器 Agent（不需要 JWT 握手拦截器）
        registry.addHandler(deviceRelayHandler, "/ws/device")
                .setAllowedOrigins("*");
    }
}
