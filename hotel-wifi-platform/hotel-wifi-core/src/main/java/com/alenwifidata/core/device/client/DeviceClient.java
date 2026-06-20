package com.alenwifidata.core.device.client;

import com.alenwifidata.core.device.model.ActiveSession;
import com.alenwifidata.core.device.model.DeviceMetrics;
import com.alenwifidata.core.device.model.RouterDevice;

import java.util.Collections;
import java.util.List;

/**
 * 设备驱动抽象接口 —— 可插拔的设备厂商驱动架构
 *
 * 每种设备类型（MIKROTIK / UNIFI / OMADA / RADIUS / ...）提供一个实现类，
 * Spring 自动注入到 DeviceClientRegistry，按 deviceType 路由。
 *
 * 参考 auth 包的 AuthProvider SPI 模式设计。
 */
public interface DeviceClient {

    /**
     * 驱动名称，与 router_device.device_type 字段匹配
     * 例: "MIKROTIK", "UNIFI", "OMADA", "RADIUS", "MERAKI", "ARUBA", "OPENWRT"
     */
    String driverName();

    // ==================== 连接 ====================

    /**
     * 测试与设备的连通性
     * @return true = 可达且认证通过
     */
    boolean testConnection(RouterDevice device);

    // ==================== 流量/会话采集 ====================

    /**
     * 获取设备上当前所有活跃用户会话
     * @return 在线用户列表，失败返回空列表
     */
    List<ActiveSession> getActiveSessions(RouterDevice device);

    // ==================== 用户控制 ====================

    /**
     * 强制踢用户下线
     * @param sessionId 设备端的会话ID
     * @return true = 成功踢下线
     */
    boolean kickUser(RouterDevice device, String sessionId);

    /**
     * 设置带宽限速规则
     * @param target 限速目标（IP/MAC/用户名）
     * @param uploadBps 上行速率限制（bps）
     * @param downloadBps 下行速率限制（bps）
     * @return true = 设置成功
     */
    boolean setBandwidthLimit(RouterDevice device, String target,
                              long uploadBps, long downloadBps);

    // ==================== 资源监控 ====================

    /**
     * 获取设备性能指标（CPU/内存/接口流量）
     * 默认返回空 —— 各驱动按能力覆写
     */
    default DeviceMetrics getMetrics(RouterDevice device) {
        return DeviceMetrics.builder()
                .driverName(driverName())
                .build();
    }

    // ==================== 工具方法 ====================

    /**
     * 判断该驱动是否适用于指定的 deviceType
     */
    default boolean supports(String deviceType) {
        return driverName().equalsIgnoreCase(deviceType);
    }

    /**
     * 驱动优先级（数字越小优先级越高）
     * 当多个驱动 supports() 返回 true 时使用
     */
    default int priority() {
        return 100;
    }
}
