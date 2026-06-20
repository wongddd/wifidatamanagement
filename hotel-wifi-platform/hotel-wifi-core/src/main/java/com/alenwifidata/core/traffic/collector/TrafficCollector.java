package com.alenwifidata.core.traffic.collector;

import com.alenwifidata.core.billing.engine.BillingEngine;
import com.alenwifidata.core.billing.mapper.OnlineSessionMapper;
import com.alenwifidata.core.billing.model.OnlineSession;
import com.alenwifidata.core.device.client.DeviceClient;
import com.alenwifidata.core.device.client.DeviceClientRegistry;
import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.model.ActiveSession;
import com.alenwifidata.core.device.model.RouterDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流量采集器 —— 定时轮询所有在线设备，采集活跃用户流量，触发计费引擎扣费
 *
 * 通过 DeviceClientRegistry 支持多厂商设备驱动（MikroTik / UniFi / RADIUS / ...）
 */
@Slf4j
@Component
public class TrafficCollector {

    private final RouterDeviceMapper deviceMapper;
    private final OnlineSessionMapper sessionMapper;
    private final DeviceClientRegistry driverRegistry;
    private final BillingEngine billingEngine;

    /** 记录每个会话上次的流量值，用于计算增量 */
    private final Map<Long, long[]> lastTrafficMap = new ConcurrentHashMap<>();

    public TrafficCollector(RouterDeviceMapper deviceMapper,
                            OnlineSessionMapper sessionMapper,
                            DeviceClientRegistry driverRegistry,
                            BillingEngine billingEngine) {
        this.deviceMapper = deviceMapper;
        this.sessionMapper = sessionMapper;
        this.driverRegistry = driverRegistry;
        this.billingEngine = billingEngine;
    }

    /**
     * 定时采集任务 — 每 30 秒执行
     */
    @Scheduled(fixedDelayString = "${billing.poll-interval-seconds:30}000")
    public void collectTraffic() {
        List<RouterDevice> onlineDevices = deviceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RouterDevice>()
                        .eq(RouterDevice::getStatus, "ONLINE")
        );

        for (RouterDevice device : onlineDevices) {
            try {
                collectFromDevice(device);
            } catch (Exception e) {
                log.error("采集设备流量异常: device={}, error={}", device.getDeviceName(), e.getMessage());
            }
        }
    }

    /**
     * 从单台设备采集流量 —— 通过 DeviceClient 驱动（支持多厂商）
     */
    private void collectFromDevice(RouterDevice device) {
        DeviceClient driver = driverRegistry.getDriver(device);
        List<ActiveSession> activeSessions = driver.getActiveSessions(device);

        if (activeSessions.isEmpty()) {
            return;
        }

        List<OnlineSession> dbSessions = sessionMapper.selectActiveByRouter(device.getId());

        for (OnlineSession dbSession : dbSessions) {
            // 在设备返回的活跃用户中查找对应会话
            ActiveSession matched = findSession(activeSessions, dbSession);
            if (matched == null) {
                // 用户在设备端已离线，结束会话
                billingEngine.endSession(dbSession.getId(), "FINISHED");
                lastTrafficMap.remove(dbSession.getId());
                log.info("检测到用户离线: sessionId={}, memberId={}", dbSession.getId(), dbSession.getMemberId());
                continue;
            }

            // 使用统一的 ActiveSession 数据
            long bytesIn = matched.getBytesIn() != null ? matched.getBytesIn() : 0;
            long bytesOut = matched.getBytesOut() != null ? matched.getBytesOut() : 0;

            billingEngine.deductTrafficFee(dbSession.getId(), bytesIn, bytesOut);

            log.debug("流量采集 [{}]: sessionId={}, bytesIn={}, bytesOut={}",
                    driver.driverName(), dbSession.getId(), bytesIn, bytesOut);
        }
    }

    /**
     * 在活跃会话列表中查找匹配的数据库会话
     */
    private ActiveSession findSession(List<ActiveSession> sessions, OnlineSession dbSession) {
        for (ActiveSession s : sessions) {
            String ip = s.getIpAddress() != null ? s.getIpAddress() : "";
            String mac = s.getMacAddress() != null ? s.getMacAddress() : "";
            if (ip.equals(dbSession.getIpAddress()) || mac.equals(dbSession.getMacAddress())) {
                return s;
            }
        }
        return null;
    }

    /**
     * 心跳检测 — 每 60 秒
     * 通过 DeviceClient 驱动测试所有设备连通性
     */
    @Scheduled(fixedDelay = 60000)
    public void heartbeatCheck() {
        List<RouterDevice> devices = deviceMapper.selectList(null);
        for (RouterDevice device : devices) {
            try {
                DeviceClient driver = driverRegistry.getDriver(device);
                boolean alive = driver.testConnection(device);
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
