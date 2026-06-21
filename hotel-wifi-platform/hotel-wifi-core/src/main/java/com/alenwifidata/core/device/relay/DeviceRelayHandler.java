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
import java.util.*;
import java.util.concurrent.*;

/**
 * 设备中继处理器 — 接收内网 Agent 的连接，实现远程管理
 *
 * 架构:
 *   公网服务器 (本Handler)  ←WebSocket→  内网 Agent (酒店局域网)
 *   Agent 定时心跳，上报设备状态
 *   服务器通过 WebSocket 下发命令给 Agent，Agent 在本地执行
 *   支持命令队列: 心跳时自动下发待执行命令
 *   支持自动发现: Agent 上报的局域网设备可自动录入
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
    /** 命令队列: deviceId -> Queue<CommandTask> (Agent 离线时积压) */
    private static final ConcurrentHashMap<Long, ConcurrentLinkedQueue<CommandTask>> COMMAND_QUEUES = new ConcurrentHashMap<>();

    private static class CommandTask {
        String command;
        Map<String, Object> params;
        String requestId;
        CommandTask(String cmd, Map<String, Object> p, String rid) {
            this.command = cmd; this.params = p; this.requestId = rid;
        }
    }

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
        Long disconnectedId = null;
        for (Map.Entry<Long, WebSocketSession> e : AGENTS.entrySet()) {
            if (e.getValue().getId().equals(session.getId())) {
                disconnectedId = e.getKey();
                break;
            }
        }
        if (disconnectedId != null) {
            AGENTS.remove(disconnectedId);
            updateDeviceStatus(disconnectedId, "OFFLINE");
            log.info("设备Agent断开: deviceId={}", disconnectedId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) msg.getOrDefault("type", "");

            switch (type) {
                case "register" -> handleRegister(session, msg);
                case "heartbeat" -> handleHeartbeat(msg);
                case "response" -> handleResponse(msg);
                case "event" -> handleEvent(msg);
                default -> log.debug("未知Agent消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("Agent消息处理异常", e);
        }
    }

    // ===== 消息处理 =====

    @SuppressWarnings("unchecked")
    private void handleRegister(WebSocketSession session, Map<String, Object> msg) {
        Long deviceId = toLong(msg.get("deviceId"));
        String secret = (String) msg.get("secret");

        RouterDevice device = deviceMapper.selectById(deviceId);
        if (device == null) {
            sendMessage(session, Map.of("type", "register_reject", "reason", "设备不存在"));
            return;
        }

        AGENTS.put(deviceId, session);
        updateDeviceStatus(deviceId, "ONLINE");

        sendMessage(session, Map.of("type", "register_ok", "deviceId", deviceId));
        log.info("设备Agent注册成功: deviceId={}, deviceName={}", deviceId, device.getDeviceName());

        // 注册成功后立即下发命令队列中的待执行命令
        drainCommandQueue(deviceId);
    }

    @SuppressWarnings("unchecked")
    private void handleHeartbeat(Map<String, Object> msg) {
        Long deviceId = toLong(msg.get("deviceId"));
        updateDeviceStatus(deviceId, "ONLINE");

        // 下发命令队列中的待执行命令
        drainCommandQueue(deviceId);
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
        String eventType = (String) msg.get("eventType");
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        log.info("Agent事件: type={}, data={}", eventType, data);

        if ("device_discovered".equals(eventType)) {
            String mac = (String) data.get("mac");
            String ip = (String) data.get("ip");
            String hostname = (String) data.get("hostname");
            log.info("内网发现设备: {} @ {} (mac={})", hostname, ip, mac);
        }
    }

    // ===== 命令队列 =====

    /**
     * 将命令加入队列，Agent 下次心跳时自动下发
     */
    public String enqueueCommand(Long deviceId, String command, Map<String, Object> params) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        CommandTask task = new CommandTask(command, params, requestId);
        COMMAND_QUEUES.computeIfAbsent(deviceId, k -> new ConcurrentLinkedQueue<>()).add(task);
        log.debug("命令入队: deviceId={}, command={}, requestId={}, queueSize={}",
                deviceId, command, requestId, queueSize(deviceId));
        return requestId;
    }

    /**
     * 清空命令队列：逐个下发到 Agent
     */
    private void drainCommandQueue(Long deviceId) {
        ConcurrentLinkedQueue<CommandTask> queue = COMMAND_QUEUES.get(deviceId);
        if (queue == null || queue.isEmpty()) return;

        WebSocketSession session = AGENTS.get(deviceId);
        if (session == null || !session.isOpen()) return;

        CommandTask task;
        int sent = 0;
        while ((task = queue.poll()) != null) {
            Map<String, Object> cmd = new LinkedHashMap<>();
            cmd.put("type", "command");
            cmd.put("requestId", task.requestId);
            cmd.put("command", task.command);
            cmd.put("params", task.params);
            sendMessage(session, cmd);
            sent++;
        }
        if (sent > 0) {
            log.info("命令队列已下发: deviceId={}, sent={}", deviceId, sent);
        }
    }

    public int queueSize(Long deviceId) {
        ConcurrentLinkedQueue<CommandTask> q = COMMAND_QUEUES.get(deviceId);
        return q == null ? 0 : q.size();
    }

    // ===== 对外 API =====

    public boolean isOnline(Long deviceId) {
        WebSocketSession session = AGENTS.get(deviceId);
        return session != null && session.isOpen();
    }

    public String sendCommandAndWait(Long deviceId, String command, Map<String, Object> params, int timeoutSeconds) {
        WebSocketSession session = AGENTS.get(deviceId);
        if (session == null || !session.isOpen()) {
            // Agent 离线 → 命令入队等待
            enqueueCommand(deviceId, command, params);
            return "{\"queued\":true,\"message\":\"Agent离线，命令已入队\"}";
        }

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<String> future = new CompletableFuture<>();
        PENDING.put(requestId, future);

        try {
            Map<String, Object> cmd = new LinkedHashMap<>();
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

    private void updateDeviceStatus(Long deviceId, String status) {
        RouterDevice device = deviceMapper.selectById(deviceId);
        if (device != null) {
            device.setStatus(status);
            device.setLastHeartbeat(LocalDateTime.now());
            deviceMapper.updateById(device);
        }
    }

    private Long toLong(Object obj) {
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof String) return Long.parseLong((String) obj);
        return 0L;
    }

    public int getOnlineAgentCount() {
        return (int) AGENTS.values().stream().filter(WebSocketSession::isOpen).count();
    }
}
