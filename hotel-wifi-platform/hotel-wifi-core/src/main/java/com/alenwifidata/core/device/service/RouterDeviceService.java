package com.alenwifidata.core.device.service;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.common.util.AESCrypto;
import com.alenwifidata.core.device.client.DeviceClient;
import com.alenwifidata.core.device.client.DeviceClientRegistry;
import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.model.RouterDevice;
import com.alenwifidata.core.billingpackage.mapper.BillingPackageMapper;
import com.alenwifidata.core.billingpackage.model.BillingPackage;
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
    private final BillingPackageMapper packageMapper;

    public RouterDeviceService(RouterDeviceMapper deviceMapper,
                               DeviceClientRegistry driverRegistry,
                               BillingPackageMapper packageMapper) {
        this.deviceMapper = deviceMapper;
        this.driverRegistry = driverRegistry;
        this.packageMapper = packageMapper;
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
        for (RouterDevice device : result.getRecords()) {
            device.setApiPassword(AESCrypto.decrypt(device.getApiPassword()));
        }
        return result;
    }

    public RouterDevice getById(Long id) {
        RouterDevice device = deviceMapper.selectById(id);
        if (device == null) throw new BusinessException(404, "设备不存在");
        device.setApiPassword(AESCrypto.decrypt(device.getApiPassword()));
        return device;
    }

    public RouterDevice create(RouterDevice device) {
        device.setTenantId(TenantContext.get());
        device.setStatus("OFFLINE");
        if (device.getApiPort() == null) device.setApiPort(8728);
        if (device.getDeviceType() == null || device.getDeviceType().isBlank())
            device.setDeviceType("MIKROTIK");
        if (device.getApiPassword() != null && !device.getApiPassword().isBlank())
            device.setApiPassword(AESCrypto.encrypt(device.getApiPassword()));
        deviceMapper.insert(device);
        device.setApiPassword(AESCrypto.decrypt(device.getApiPassword()));
        return device;
    }

    public RouterDevice update(RouterDevice device) {
        getById(device.getId());
        if (device.getApiPassword() != null && !device.getApiPassword().isBlank())
            device.setApiPassword(AESCrypto.encrypt(device.getApiPassword()));
        deviceMapper.updateById(device);
        return getById(device.getId());
    }

    public void delete(Long id) { getById(id); deviceMapper.deleteById(id); }

    public Map<String, Object> testConnection(Long id) {
        RouterDevice device = getById(id);
        DeviceClient driver = driverRegistry.getDriver(device);
        boolean connected = driver.testConnection(device);
        device.setStatus(connected ? "ONLINE" : "ERROR");
        device.setLastHeartbeat(LocalDateTime.now());
        deviceMapper.updateById(device);
        return Map.of("connected", connected, "status", device.getStatus(), "driver", driver.driverName());
    }

    /**
     * 同步配置到设备 — 通过 DeviceClient 将套餐限速规则下发到路由器
     */
    public Map<String, Object> syncConfig(Long id) {
        RouterDevice device = getById(id);
        DeviceClient driver = driverRegistry.getDriver(device);
        int synced = 0;

        try {
            var packages = packageMapper.selectList(
                    new LambdaQueryWrapper<BillingPackage>()
                            .eq(BillingPackage::getStatus, 1)
                            .eq(BillingPackage::getTenantId, device.getTenantId()));
            for (BillingPackage pkg : packages) {
                long up = pkg.getUploadLimitBps() != null ? pkg.getUploadLimitBps() : 5_242_880;
                long down = pkg.getDownloadLimitBps() != null ? pkg.getDownloadLimitBps() : 10_485_760;
                if (driver.setBandwidthLimit(device, "pkg-" + pkg.getId(), up, down)) {
                    synced++;
                }
            }
        } catch (Exception e) {
            log.warn("同步限速规则部分失败: device={}, error={}", device.getDeviceName(), e.getMessage());
        }

        device.setLastSyncAt(LocalDateTime.now());
        deviceMapper.updateById(device);

        return Map.of("success", true, "syncedAt", device.getLastSyncAt().toString(),
                "rulesSynced", synced, "driver", driver.driverName());
    }

    public String getDriverName(Long id) {
        return driverRegistry.getDriver(getById(id)).driverName();
    }
}
