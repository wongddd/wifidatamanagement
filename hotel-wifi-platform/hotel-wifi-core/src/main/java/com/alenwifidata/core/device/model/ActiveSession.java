package com.alenwifidata.core.device.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一活跃会话模型 —— 各设备驱动采集后统一为此格式
 * 消除 MikroTik JSON / UniFi JSON / RADIUS 数据包之间的差异
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveSession {

    /** 设备端的会话ID（MikroTik .id / UniFi _id / RADIUS Acct-Session-Id） */
    private String sessionId;

    /** 用户名 */
    private String username;

    /** MAC 地址 */
    private String macAddress;

    /** IP 地址 */
    private String ipAddress;

    /** 已下载字节数 */
    private Long bytesIn;

    /** 已上传字节数 */
    private Long bytesOut;

    /** 上线时间 */
    private LocalDateTime loginAt;

    /** 在线时长（秒） */
    private Long uptimeSeconds;

    /** 驱动名称（来源设备类型） */
    private String driverName;
}
