package com.alenwifidata.core.device.service;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.device.client.DeviceClient;
import com.alenwifidata.core.device.client.DeviceClientRegistry;
import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.model.RouterDevice;
import com.alenwifidata.core.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class RouterDeviceService {

    private final RouterDeviceMapper deviceMapper;
    private final DeviceClientRegistry driverRegistry;

    public RouterDeviceService(RouterDeviceMapper deviceMapper, DeviceClientRegistry driverRegistry) {
        this.deviceMapper = deviceMapper;
        this.driverRegistry = driverRegistry;
    }

    public Page<RouterDevice> page(int pageNum, int pageSize, Long hotelId, String keyword) {
        Long tenantId = TenantContext.get();
        LambdaQueryWrapper<RouterDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RouterDevice::getTenantId, tenantId);
        if (hotelId != null) {
            wrapper.eq(RouterDevice::getHotelId, hotelId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(RouterDevice::getDeviceName, keyword)
                             .or().like(RouterDevice::getHost, keyword));
        }
        wrapper.orderByDesc(RouterDevice::getCreatedAt);
        return deviceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public RouterDevice getById(Long id) {
        RouterDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        return device;
    }

    public RouterDevice create(RouterDevice device) {
        device.setTenantId(TenantContext.get());
        device.setStatus("OFFLINE");
        if (device.getApiPort() == null) {
            device.setApiPort(8728);
        }
        if (device.getDeviceType() == null || device.getDeviceType().isBlank()) {
            device.setDeviceType("MIKROTIK");
        }
        // TODO: AES 加密密码
        deviceMapper.insert(device);
        return device;
    }

    public RouterDevice update(RouterDevice device) {
        getById(device.getId());
        if (device.getApiPassword() != null) {
            // TODO: AES 加密密码
        }
        deviceMapper.updateById(device);
        return getById(device.getId());
    }

    public void delete(Long id) {
        getById(id);
        deviceMapper.deleteById(id);
    }

    /**
     * 测试设备连接 —— 通过 DeviceClient 驱动（支持多厂商）
     */
    public Map<String, Object> testConnection(Long id) {
        RouterDevice device = getById(id);
        DeviceClient driver = driverRegistry.getDriver(device);
        boolean connected = driver.testConnection(device);

        device.setStatus(connected ? "ONLINE" : "ERROR");
        device.setLastHeartbeat(LocalDateTime.now());
        deviceMapper.updateById(device);

        return Map.of(
                "connected", connected,
                "status", device.getStatus(),
                "driver", driver.driverName()
        );
    }

    /**
     * 同步配置到设备
     */
    public Map<String, Object> syncConfig(Long id) {
        RouterDevice device = getById(id);
        // TODO: 同步 Hotspot Profile、Walled Garden 等配置（通过 DeviceClient）
        device.setLastSyncAt(LocalDateTime.now());
        deviceMapper.updateById(device);

        return Map.of("success", true, "syncedAt", device.getLastSyncAt().toString());
    }

    /**
     * 获取设备当前使用的驱动名称
     */
    public String getDriverName(Long id) {
        RouterDevice device = getById(id);
        return driverRegistry.getDriver(device).driverName();
    }
}
