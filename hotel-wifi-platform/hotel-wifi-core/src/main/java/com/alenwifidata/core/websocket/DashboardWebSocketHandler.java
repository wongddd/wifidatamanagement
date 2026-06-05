package com.alenwifidata.core.websocket;

import com.alenwifidata.core.billing.mapper.OnlineSessionMapper;
import com.alenwifidata.core.billing.mapper.BillingDeductionMapper;
import com.alenwifidata.core.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dashboard 实时数据推送 WebSocket
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private final OnlineSessionMapper sessionMapper;
    private final BillingDeductionMapper deductionMapper;
    private final ObjectMapper objectMapper;

    /** 连接池: tenantId -> sessions */
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<String, WebSocketSession>> TENANT_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long tenantId = (Long) session.getAttributes().get("tenantId");
        if (tenantId != null) {
            TENANT_SESSIONS.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .put(session.getId(), session);
            log.info("WebSocket 连接: sessionId={}, tenantId={}", session.getId(), tenantId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long tenantId = (Long) session.getAttributes().get("tenantId");
        if (tenantId != null) {
            var sessions = TENANT_SESSIONS.get(tenantId);
            if (sessions != null) {
                sessions.remove(session.getId());
            }
        }
    }

    /**
     * 每 10 秒推送实时数据
     */
    @Scheduled(fixedDelay = 10000)
    public void pushDashboardData() {
        if (TENANT_SESSIONS.isEmpty()) return;

        TENANT_SESSIONS.forEach((tenantId, sessions) -> {
            if (sessions.isEmpty()) return;

            try {
                LocalDateTime todayStart = LocalDate.now().atStartOfDay();
                LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

                int onlineCount = sessionMapper.selectActiveSessions(tenantId).size();
                BigDecimal todayRevenue = deductionMapper.sumByTimeRange(tenantId, todayStart, todayEnd);
                if (todayRevenue == null) todayRevenue = BigDecimal.ZERO;

                Map<String, Object> data = Map.of(
                        "type", "dashboard",
                        "onlineCount", onlineCount,
                        "todayRevenue", todayRevenue.doubleValue(),
                        "timestamp", System.currentTimeMillis()
                );

                String json = objectMapper.writeValueAsString(data);
                TextMessage message = new TextMessage(json);

                for (WebSocketSession session : sessions.values()) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(message);
                        } catch (IOException e) {
                            log.debug("WebSocket 发送失败: {}", session.getId());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("WebSocket 推送异常: tenantId={}", tenantId, e);
            }
        });
    }
}
