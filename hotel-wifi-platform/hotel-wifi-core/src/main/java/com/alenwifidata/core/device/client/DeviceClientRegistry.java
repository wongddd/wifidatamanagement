package com.alenwifidata.core.device.client;

import com.alenwifidata.core.device.model.RouterDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备驱动注册中心 —— 根据 deviceType 路由到对应的 DeviceClient 实现
 *
 * Spring 自动注入所有 DeviceClient 实现类，按 driverName() 注册。
 * 原所有硬编码 `mikroTikClient.xxx(device)` 全部替换为:
 *   DeviceClient driver = registry.getDriver(device);
 *   driver.xxx(device);
 */
@Slf4j
@Component
public class DeviceClientRegistry {

    private final Map<String, DeviceClient> drivers = new ConcurrentHashMap<>();
    private final List<DeviceClient> sortedDrivers;

    /**
     * Spring 自动注入所有 DeviceClient 的实现 Bean
     */
    public DeviceClientRegistry(List<DeviceClient> clients) {
        this.sortedDrivers = clients.stream()
                .sorted(Comparator.comparingInt(DeviceClient::priority))
                .toList();
        for (DeviceClient client : clients) {
            drivers.put(client.driverName().toUpperCase(), client);
            log.info("注册设备驱动: {} (priority={})", client.driverName(), client.priority());
        }
        log.info("DeviceClientRegistry 初始化完成，共注册 {} 个驱动", drivers.size());
    }

    /**
     * 根据 deviceType 获取驱动
     * 未匹配时返回默认驱动（MIKROTIK）
     */
    public DeviceClient getDriver(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return getDefaultDriver();
        }
        DeviceClient driver = drivers.get(deviceType.toUpperCase());
        if (driver != null) {
            return driver;
        }
        // 遍历所有驱动，通过 supports() 匹配
        for (DeviceClient client : sortedDrivers) {
            if (client.supports(deviceType)) {
                return client;
            }
        }
        log.warn("未找到 deviceType={} 的驱动，使用默认驱动", deviceType);
        return getDefaultDriver();
    }

    /**
     * 根据设备实体获取驱动
     */
    public DeviceClient getDriver(RouterDevice device) {
        return getDriver(device.getDeviceType());
    }

    /**
     * 获取默认驱动（MikroTik，向后兼容）
     */
    public DeviceClient getDefaultDriver() {
        DeviceClient driver = drivers.get("MIKROTIK");
        if (driver == null && !drivers.isEmpty()) {
            driver = drivers.values().iterator().next();
        }
        return driver;
    }

    /**
     * 获取所有已注册的驱动名称
     */
    public List<String> getRegisteredDriverNames() {
        return List.copyOf(drivers.keySet());
    }

    /**
     * 检查指定 deviceType 是否有对应驱动
     */
    public boolean hasDriver(String deviceType) {
        return drivers.containsKey(deviceType.toUpperCase());
    }
}
