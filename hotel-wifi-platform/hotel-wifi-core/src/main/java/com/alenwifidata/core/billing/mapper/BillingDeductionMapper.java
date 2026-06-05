package com.alenwifidata.core.billing.mapper;

import com.alenwifidata.core.billing.model.BillingDeduction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BillingDeductionMapper extends BaseMapper<BillingDeduction> {

    @Select("SELECT COALESCE(SUM(amount), 0) FROM billing_deduction " +
            "WHERE tenant_id = #{tenantId} AND created_at BETWEEN #{start} AND #{end}")
    BigDecimal sumByTimeRange(@Param("tenantId") Long tenantId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(bytes_delta), 0) FROM billing_deduction " +
            "WHERE tenant_id = #{tenantId} AND created_at BETWEEN #{start} AND #{end}")
    Long sumTrafficByTimeRange(@Param("tenantId") Long tenantId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Select("SELECT DATE(created_at) as date, COALESCE(SUM(amount), 0) as amount " +
            "FROM billing_deduction WHERE tenant_id = #{tenantId} " +
            "AND created_at BETWEEN #{start} AND #{end} " +
            "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> dailyRevenue(@Param("tenantId") Long tenantId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);
}
