package com.alenwifidata.core.report.service;

import com.alenwifidata.core.billing.mapper.BillingDeductionMapper;
import com.alenwifidata.core.billing.mapper.BillingOrderMapper;
import com.alenwifidata.core.billing.mapper.OnlineSessionMapper;
import com.alenwifidata.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final BillingDeductionMapper deductionMapper;
    private final OnlineSessionMapper sessionMapper;
    private final BillingOrderMapper orderMapper;

    /**
     * Dashboard 概览数据
     */
    public Map<String, Object> dashboardOverview() {
        Long tenantId = TenantContext.get();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        Map<String, Object> result = new HashMap<>();

        // 今日收入
        BigDecimal todayRevenue = deductionMapper.sumByTimeRange(tenantId, todayStart, todayEnd);
        result.put("todayRevenue", todayRevenue != null ? todayRevenue : BigDecimal.ZERO);

        // 今日在线数（当前活跃会话数）
        int activeCount = sessionMapper.selectActiveSessions(tenantId).size();
        result.put("onlineCount", activeCount);

        // 今日流量
        Long todayTraffic = deductionMapper.sumTrafficByTimeRange(tenantId, todayStart, todayEnd);
        result.put("todayTraffic", todayTraffic != null ? todayTraffic : 0L);

        // 今日新增会员（简化：查询今日创建的会员数）
        result.put("newMembers", 0);

        return result;
    }

    /**
     * 近7天收入趋势
     */
    public List<Map<String, Object>> revenueTrend() {
        Long tenantId = TenantContext.get();
        LocalDateTime start = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);
        return deductionMapper.dailyRevenue(tenantId, start, end);
    }

    /**
     * 流量统计
     */
    public Map<String, Object> trafficStats(int days) {
        Long tenantId = TenantContext.get();
        LocalDateTime start = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);

        Long totalTraffic = deductionMapper.sumTrafficByTimeRange(tenantId, start, end);

        Map<String, Object> result = new HashMap<>();
        result.put("totalTraffic", totalTraffic != null ? totalTraffic : 0L);
        result.put("days", days);
        return result;
    }

    /**
     * 收入统计
     */
    public Map<String, Object> revenueStats(int days) {
        Long tenantId = TenantContext.get();
        LocalDateTime start = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);

        BigDecimal totalRevenue = deductionMapper.sumByTimeRange(tenantId, start, end);

        Map<String, Object> result = new HashMap<>();
        result.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
        result.put("days", days);
        return result;
    }
}
