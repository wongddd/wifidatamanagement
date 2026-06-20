package com.alenwifidata.core.device.radius;

import com.alenwifidata.core.device.client.DeviceClient;
import com.alenwifidata.core.device.model.ActiveSession;
import com.alenwifidata.core.device.model.DeviceMetrics;
import com.alenwifidata.core.device.model.RouterDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * RADIUS 设备驱动 —— 实现 DeviceClient 接口
 *
 * RADIUS 设备通过标准的 UDP 协议与本系统的 RADIUSServer 通信，
 * 本驱动不主动连接设备，而是标记设备状态、提供 RADIUS 特定的会话管理。
 *
 * 支持: 华为 ME60/MA5800、H3C SR88/CR16000、Cisco ASR1000、Juniper MX
 *       及任何标准 RADIUS NAS 设备
 */
@Slf4j
@Component
public class RADIUSClient implements DeviceClient {

    @Override
    public String driverName() {
        return "RADIUS";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean supports(String deviceType) {
        // RADIUS 驱动支持多种 deviceType 标记
        return "RADIUS".equalsIgnoreCase(deviceType)
                || "HUAWEI".equalsIgnoreCase(deviceType)
                || "H3C".equalsIgnoreCase(deviceType)
                || "CISCO".equalsIgnoreCase(deviceType)
                || "JUNIPER".equalsIgnoreCase(deviceType);
    }

    @Override
    public boolean testConnection(RouterDevice device) {
        // RADIUS 设备连通性通过 RADIUS Server 收到的 Accounting 包来验证
        // 本方法仅做基础检查：设备配置完整即视为可工作
        if (device.getHost() == null || device.getHost().isBlank()) {
            return false;
        }
        if (device.getApiPassword() == null || device.getApiPassword().isBlank()) {
            log.warn("RADIUS 设备 {} 未配置共享密钥(apiPassword字段)", device.getDeviceName());
        }
        log.debug("RADIUS 设备 {} ({}) 配置完整，等待 Accounting 包验证连通性",
                device.getDeviceName(), device.getHost());
        return true; // RADIUS 设备通过接收包来验证，配置完整即返回 true
    }

    @Override
    public List<ActiveSession> getActiveSessions(RouterDevice device) {
        // RADIUS 会话由 RADIUSServer 在处理 Accounting-Request Start/Stop 时管理
        // 会话状态存储在 online_session 表中，由 TrafficCollector 统一采集
        // 本方法无需主动拉取
        log.debug("RADIUS 会话由 RADIUSServer 被动管理: device={}", device.getDeviceName());
        return Collections.emptyList();
    }

    @Override
    public boolean kickUser(RouterDevice device, String sessionId) {
        // RADIUS 协议支持 DM (Disconnect-Message, RFC 3576) 来主动踢用户下线
        // 本方法发送 CoA/Disconnect 请求到 NAS
        // TODO: 实现 RFC 3576 DM (Disconnect-Message) 发送
        log.warn("RADIUS Disconnect-Message 尚未实现: device={}, sessionId={}",
                device.getDeviceName(), sessionId);
        return false;
    }

    @Override
    public boolean setBandwidthLimit(RouterDevice device, String target,
                                     long uploadBps, long downloadBps) {
        // RADIUS 带宽限制通过 CoA (Change-of-Authorization, RFC 3576) 实现
        // TODO: 实现 RFC 3576 CoA 消息发送
        log.warn("RADIUS CoA 尚未实现: device={}, target={}", device.getDeviceName(), target);
        return false;
    }

    @Override
    public DeviceMetrics getMetrics(RouterDevice device) {
        return DeviceMetrics.builder()
                .driverName(driverName())
                .clientCount(0)
                .build();
    }
}
