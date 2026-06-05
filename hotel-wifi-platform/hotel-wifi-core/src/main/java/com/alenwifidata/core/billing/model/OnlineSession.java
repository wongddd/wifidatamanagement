package com.alenwifidata.core.billing.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 上网会话实体
 */
@Data
@TableName("online_session")
public class OnlineSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long hotelId;
    private Long memberId;
    private Long routerId;
    private String sessionId;
    private String macAddress;
    private String ipAddress;
    private Long packageId;
    private LocalDateTime loginAt;
    private LocalDateTime logoutAt;
    private Long totalBytesIn;
    private Long totalBytesOut;
    private BigDecimal totalCost;
    private String status;         // ACTIVE / FINISHED / KICKED
    private String logoutReason;
    private LocalDateTime createdAt;
}
