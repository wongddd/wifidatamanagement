package com.alenwifidata.core.device.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 路由器设备实体
 */
@Data
@TableName("router_device")
public class RouterDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long hotelId;
    private String deviceName;
    private String deviceType;
    private String host;
    private Integer apiPort;
    private String apiUser;
    private String apiPassword;
    private String hotspotServer;
    private String addressPool;
    private String wanInterface;
    private String lanInterface;
    private LocalDateTime lastSyncAt;
    private LocalDateTime lastHeartbeat;
    private String status;         // ONLINE / OFFLINE / ERROR / MAINTENANCE

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
