package com.alenwifidata.core.billing.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@TableName("billing_order")
public class BillingOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long tenantId;
    private Long hotelId;
    private Long memberId;
    private Long packageId;
    private BigDecimal amount;
    private String payType;        // BALANCE / CARD / WECHAT / ALIPAY
    private String status;         // PENDING / PAID / CANCELLED / REFUNDED
    private String remark;
    private LocalDateTime paidAt;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
