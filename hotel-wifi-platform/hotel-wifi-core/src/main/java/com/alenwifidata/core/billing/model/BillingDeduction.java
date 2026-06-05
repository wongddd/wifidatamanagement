package com.alenwifidata.core.billing.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 扣费记录实体
 */
@Data
@TableName("billing_deduction")
public class BillingDeduction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long memberId;
    private Long sessionId;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long bytesDelta;
    private String deductionType;   // PREPAID / SETTLE / REFUND
    private LocalDateTime createdAt;
}
