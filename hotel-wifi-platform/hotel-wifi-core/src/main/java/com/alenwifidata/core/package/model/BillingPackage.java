package com.alenwifidata.core.package.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐实体
 */
@Data
@TableName("billing_package")
public class BillingPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String packageName;
    private String description;
    private String billingType;    // TIME / TRAFFIC / HYBRID
    private Long durationSeconds;
    private Long trafficBytes;
    private BigDecimal price;
    private BigDecimal maxCost;
    private Integer maxDevices;
    private Long uploadLimitBps;
    private Long downloadLimitBps;
    private Integer maxConnections;
    private Integer sortOrder;
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
