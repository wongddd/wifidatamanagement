package com.alenwifidata.core.device.snmp;

import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.model.RouterDevice;
import com.alenwifidata.core.device.snmp.SnmpMonitor.SnmpResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SNMP 采集调度器 — 每5分钟采集一次所有设备状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnmpCollector {

    private final RouterDeviceMapper deviceMapper;
    private final SnmpMonitor snmpMonitor;

    /** 设备最新监控数据缓存 */
    private final Map<Long, SnmpResult> latestResults = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 300000) // 5分钟
    public void collectAll() {
        List<RouterDevice> devices = deviceMapper.selectList(null);
        log.debug("SNMP采集开始: {} 台设备", devices.size());

        for (RouterDevice device : devices) {
            if (device.getDeviceType() != null && !"MIKROTIK".equals(device.getDeviceType())) {
                // 非 MikroTik 设备走 SNMP
                try {
                    SnmpResult result = snmpMonitor.collect(device);
                    latestResults.put(device.getId(), result);

                    // 更新设备状态
                    if (result.reachable) {
                        device.setStatus("ONLINE");
                        device.setLastHeartbeat(LocalDateTime.now());
                    } else {
                        device.setStatus("ERROR");
                    }
                    deviceMapper.updateById(device);

                } catch (Exception e) {
                    log.warn("SNMP采集异常 {}: {}", device.getDeviceName(), e.getMessage());
                }
            }
        }
        log.debug("SNMP采集完成");
    }

    /**
     * 获取设备最新监控数据
     */
    public SnmpResult getLatest(Long deviceId) {
        return latestResults.get(deviceId);
    }

    public Map<Long, SnmpResult> getAllLatest() {
        return Map.copyOf(latestResults);
    }
}
