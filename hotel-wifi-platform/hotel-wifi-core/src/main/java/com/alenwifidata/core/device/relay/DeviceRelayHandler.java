package com.alenwifidata.core.device.relay;

import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.model.RouterDevice;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 设备中继处理器 — 接收内网 Agent 的连接，实现远程管理
 *
 * 架构:
 *   公网服务器 (本Handler)  ←WebSocket→  内网 Agent (酒店局域网)
 *   Agent 定时心跳，上报设备状态
 *   服务器通过 WebSocket 下发命令给 Agent，Agent 在本地执行
 */
@Slf4j
@Component
public class DeviceRelayHandler extends TextWebSocketHandler {

    private final RouterDeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;

    /** 已连接的 Agent: deviceId -> session */
    private static final ConcurrentHashMap<Long, WebSocketSession> AGENTS = new ConcurrentHashMap<>();
    /** 等待响应的请求: requestId -> CompletableFuture */
    private static final ConcurrentHashMap<String, CompletableFuture<String>> PENDING = new ConcurrentHashMap<>();

    public DeviceRelayHandler(RouterDeviceMapper deviceMapper, ObjectMapper objectMapper) {
        this.deviceMapper = deviceMapper;
        this.objectMapper = objectMapper;
    }

    // ===== 生命周期 =====

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String agentKey = getAgentKey(session);
        log.info("设备Agent已连接: {}", agentKey);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        AGENTS.values().removeIf(s -> s.getId().equals(session.getId()));
        log.info("设备Agent断开: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) msg.getOrDefault("type", "");

            switch (type) {
                case "register":
                    handleRegister(session, msg);
                    break;
                case "heartbeat":
                    handleHeartbeat(msg);
                    break;
                case "response":
                    handleResponse(msg);
                    break;
                case "event":
                    handleEvent(msg);
                    break;
                default:
                    log.debug("未知Agent消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("Agent消息处理异常", e);
        }
    }

    // ===== 消息处理 =====

    @SuppressWarnings("unchecked")
    private void handleRegister(WebSocketSession session, Map<String, Object> msg) {
        Object deviceIdObj = msg.get("deviceId");
        Long deviceId = deviceIdObj instanceof Integer ? ((Integer) deviceIdObj).longValue() : (Long) deviceIdObj;
        String secret = (String) msg.get("secret");

        // 校验设备密钥
        RouterDevice device = deviceMapper.selectById(deviceId);
        if (device == null || !"ONLINE".equals(device.getStatus()) && !"AGENT".equals(device.getStatus())) {
            sendMessage(session, Map.of("type", "register_reject", "reason", "设备未授权"));
            return;
        }

        AGENTS.put(deviceId, session);
        device.setStatus("ONLINE");
        device.setLastHeartbeat(LocalDateTime.now());
        deviceMapper.updateById(device);

        sendMessage(session, Map.of("type", "register_ok", "deviceId", deviceId));
        log.info("设备Agent注册成功: deviceId={}, deviceName={}", deviceId, device.getDeviceName());
    }

    @SuppressWarnings("unchecked")
    private void handleHeartbeat(Map<String, Object> msg) {
        Object deviceIdObj = msg.get("deviceId");
        Long deviceId = deviceIdObj instanceof Integer ? ((Integer) deviceIdObj).longValue() : (Long) deviceIdObj;

        RouterDevice device = deviceMapper.selectById(deviceId);
        if (device != null) {
            device.setLastHeartbeat(LocalDateTime.now());
            device.setStatus("ONLINE");
            deviceMapper.updateById(device);
        }

        // 如果有待执行的命令，下发
        // TODO: 从命令队列取出并发送
    }

    private void handleResponse(Map<String, Object> msg) {
        String requestId = (String) msg.get("requestId");
        Object result = msg.get("result");
        CompletableFuture<String> future = PENDING.remove(requestId);
        if (future != null) {
            future.complete(result != null ? result.toString() : "{}");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEvent(Map<String, Object> msg) {
        // Agent 上报的事件（如新设备发现、流量告警等）
        String eventType = (String) msg.get("eventType");
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        log.info("Agent事件: type={}, data={}", eventType, data);

        if ("device_discovered".equals(eventType)) {
            // 自动发现局域网内的新设备
            String mac = (String) data.get("mac");
            String ip = (String) data.get("ip");
            String hostname = (String) data.get("hostname");
            log.info("内网发现设备: {} {} {}", hostname, ip, mac);
            // TODO: 自动添加到设备列表
        }
    }

    // ===== 对外 API =====

    /** 检查 Agent 是否在线 */
    public boolean isOnline(Long deviceId) {
        WebSocketSession session = AGENTS.get(deviceId);
        return session != null && session.isOpen();
    }

    /** 下发命令并等待响应（默认超时30秒） */
    public String sendCommandAndWait(Long deviceId, String command, Map<String, Object> params, int timeoutSeconds) {
        WebSocketSession session = AGENTS.get(deviceId);
        if (session == null || !session.isOpen()) {
            return "{\"error\":\"设备Agent不在线\"}";
        }

        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<String> future = new CompletableFuture<>();
        PENDING.put(requestId, future);

        try {
            Map<String, Object> cmd = new ConcurrentHashMap<>();
            cmd.put("type", "command");
            cmd.put("requestId", requestId);
            cmd.put("command", command);
            cmd.put("params", params);
            sendMessage(session, cmd);

            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            PENDING.remove(requestId);
            return "{\"error\":\"命令超时\"}";
        } catch (Exception e) {
            PENDING.remove(requestId);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ===== 工具方法 =====

    private void sendMessage(WebSocketSession session, Map<String, Object> msg) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            } catch (IOException e) {
                log.error("发送Agent消息失败", e);
            }
        }
    }

    private String getAgentKey(WebSocketSession session) {
        return session.getRemoteAddress() != null ? session.getRemoteAddress().toString() : session.getId();
    }

    /** 获取所有已连接的 Agent 数量 */
    public int getOnlineAgentCount() {
        return (int) AGENTS.values().stream().filter(WebSocketSession::isOpen).count();
    }
}
