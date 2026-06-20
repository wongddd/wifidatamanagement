package com.alenwifidata.core.device.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一设备性能指标模型
 * 各驱动采集 CPU/内存/接口流量后统一为此格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMetrics {

    /** CPU 负载百分比字符串，如 "35%" */
    private String cpuLoad;

    /** 已用内存（字节） */
    private Long memoryUsed;

    /** 总内存（字节） */
    private Long memoryTotal;

    /** 系统运行时间（秒） */
    private Long uptimeSeconds;

    /** 接口名 → 当前速率(bps)，如 {"ether1": 10485760, "wlan1": 5242880} */
    private Map<String, Long> interfaceTraffic;

    /** 在线客户端数量 */
    private Integer clientCount;

    /** 驱动名称 */
    private String driverName;
}
