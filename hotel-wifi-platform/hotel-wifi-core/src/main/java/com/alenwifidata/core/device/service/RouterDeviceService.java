package com.alenwifidata.core.device.service;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.common.util.AESCrypto;
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
        Page<RouterDevice> result = deviceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 解密所有记录的密码
        for (RouterDevice device : result.getRecords()) {
            device.setApiPassword(AESCrypto.decrypt(device.getApiPassword()));
        }
        return result;
    }

    public RouterDevice getById(Long id) {
        RouterDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        // 解密密码供驱动使用
        device.setApiPassword(AESCrypto.decrypt(device.getApiPassword()));
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
        // AES-256-GCM 加密密码
        if (device.getApiPassword() != null && !device.getApiPassword().isBlank()) {
            device.setApiPassword(AESCrypto.encrypt(device.getApiPassword()));
        }
        deviceMapper.insert(device);
        // 插入后解密内存中的密码（方便后续使用）
        device.setApiPassword(AESCrypto.decrypt(device.getApiPassword()));
        return device;
    }

    public RouterDevice update(RouterDevice device) {
        getById(device.getId()); // 校验存在性
        if (device.getApiPassword() != null && !device.getApiPassword().isBlank()) {
            // 密码变更时重新加密（此时为明文，需加密后入库）
            device.setApiPassword(AESCrypto.encrypt(device.getApiPassword()));
        }
        deviceMapper.updateById(device);
        RouterDevice updated = getById(device.getId());
        return updated;
    }

    public void delete(Long id) {
        getById(id);
        deviceMapper.deleteById(id);
    }

    /**
     * 测试设备连接 —— 通过 DeviceClient 驱动（支持多厂商）
     */
    public Map<String, Object> testConnection(Long id) {
        RouterDevice device = getById(id); // 已解密
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
