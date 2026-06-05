package com.alenwifidata.core.traffic.collector;

import com.alenwifidata.core.billing.engine.BillingEngine;
import com.alenwifidata.core.billing.mapper.OnlineSessionMapper;
import com.alenwifidata.core.billing.model.OnlineSession;
import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.mikrotik.MikroTikClient;
import com.alenwifidata.core.device.model.RouterDevice;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流量采集器 —— 定时轮询 MikroTik 活跃用户，触发计费引擎扣费
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrafficCollector {

    private final RouterDeviceMapper deviceMapper;
    private final OnlineSessionMapper sessionMapper;
    private final MikroTikClient mikroTikClient;
    private final BillingEngine billingEngine;

    // 记录每个会话上次的流量值，用于计算增量
    private final Map<Long, long[]> lastTrafficMap = new ConcurrentHashMap<>();

    /**
     * 定时采集任务 — 每 30 秒执行
     */
    @Scheduled(fixedDelayString = "${billing.poll-interval-seconds:30}000")
    public void collectTraffic() {
        // 查询所有在线设备
        List<RouterDevice> onlineDevices = deviceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RouterDevice>()
                        .eq(RouterDevice::getStatus, "ONLINE")
        );

        for (RouterDevice device : onlineDevices) {
            try {
                collectFromDevice(device);
            } catch (Exception e) {
                log.error("采集路由器 {} 流量异常: {}", device.getDeviceName(), e.getMessage());
                // 单台设备失败不影响其他设备
            }
        }
    }

    /**
     * 从单台设备采集流量
     */
    private void collectFromDevice(RouterDevice device) throws IOException {
        JsonNode activeUsers = mikroTikClient.getActiveHotspotUsers(device);
        if (activeUsers == null || !activeUsers.isArray()) {
            return;
        }

        List<OnlineSession> activeSessions = sessionMapper.selectActiveByRouter(device.getId());

        for (OnlineSession session : activeSessions) {
            // 在 MikroTik 返回的活跃用户中查找该会话
            String user = findUserBySession(activeUsers, session);
            if (user == null) {
                // 用户在 MikroTik 中已离线，结束会话
                billingEngine.endSession(session.getId(), "FINISHED");
                lastTrafficMap.remove(session.getId());
                log.info("检测到用户离线: sessionId={}, memberId={}", session.getId(), session.getMemberId());
                continue;
            }

            // 提取流量数据
            try {
                String bytesInStr = activeUsers.get(0).has("bytes-in")
                        ? activeUsers.get(0).get("bytes-in").asText() : "0";
                String bytesOutStr = activeUsers.get(0).has("bytes-out")
                        ? activeUsers.get(0).get("bytes-out").asText() : "0";

                long bytesIn = Long.parseLong(bytesInStr);
                long bytesOut = Long.parseLong(bytesOutStr);

                // 触发计费引擎扣费
                billingEngine.deductTrafficFee(session.getId(), bytesIn, bytesOut);

                // 记录快照（TODO: 异步批量写入 traffic_snapshot 表）
                log.debug("流量采集: sessionId={}, bytesIn={}, bytesOut={}", session.getId(), bytesIn, bytesOut);

            } catch (Exception e) {
                log.error("解析流量数据失败: sessionId={}, error={}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * 在活跃用户中查找对应会话
     */
    private String findUserBySession(JsonNode activeUsers, OnlineSession session) {
        for (JsonNode user : activeUsers) {
            String ip = user.has("address") ? user.get("address").asText() : "";
            String mac = user.has("mac-address") ? user.get("mac-address").asText() : "";
            if (ip.equals(session.getIpAddress()) || mac.equals(session.getMacAddress())) {
                return user.has("user") ? user.get("user").asText() : "";
            }
        }
        return null;
    }

    /**
     * 心跳检测 — 每 60 秒
     */
    @Scheduled(fixedDelay = 60000)
    public void heartbeatCheck() {
        List<RouterDevice> devices = deviceMapper.selectList(null);
        for (RouterDevice device : devices) {
            try {
                boolean alive = mikroTikClient.testConnection(device);
                device.setStatus(alive ? "ONLINE" : "OFFLINE");
                device.setLastHeartbeat(LocalDateTime.now());
                deviceMapper.updateById(device);
            } catch (Exception e) {
                device.setStatus("ERROR");
                deviceMapper.updateById(device);
            }
        }
    }
}
