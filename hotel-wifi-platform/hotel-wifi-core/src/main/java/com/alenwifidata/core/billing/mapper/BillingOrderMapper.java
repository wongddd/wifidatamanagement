package com.alenwifidata.core.billing.mapper;

import com.alenwifidata.core.billing.model.BillingOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface BillingOrderMapper extends BaseMapper<BillingOrder> {

    @Select("SELECT COALESCE(SUM(amount), 0) FROM billing_order " +
            "WHERE tenant_id = #{tenantId} AND status = 'PAID' AND paid_at BETWEEN #{start} AND #{end}")
    BigDecimal sumPaidByTimeRange(@Param("tenantId") Long tenantId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}
