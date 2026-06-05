package com.alenwifidata.core.recharge.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值卡实体
 */
@Data
@TableName("recharge_card")
public class RechargeCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String batchNo;
    private String cardNo;
    private String cardPassword;
    private BigDecimal amount;
    private Long packageId;
    private String status;         // UNUSED/USED/EXPIRED/REVOKED
    private Long usedBy;
    private LocalDateTime usedAt;
    private LocalDateTime expireAt;

    @TableLogic
    private Integer deleted;

    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
