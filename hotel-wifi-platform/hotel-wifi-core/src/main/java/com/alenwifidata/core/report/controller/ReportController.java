package com.alenwifidata.core.report.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.core.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/traffic")
    public ApiResult<Map<String, Object>> traffic(@RequestParam(defaultValue = "7") int days) {
        return ApiResult.ok(reportService.trafficStats(days));
    }

    @GetMapping("/revenue")
    public ApiResult<Map<String, Object>> revenue(@RequestParam(defaultValue = "7") int days) {
        return ApiResult.ok(reportService.revenueStats(days));
    }

    @GetMapping("/revenue-trend")
    public ApiResult<List<Map<String, Object>>> revenueTrend() {
        return ApiResult.ok(reportService.revenueTrend());
    }
}

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
class DashboardController {

    private final ReportService reportService;

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        return ApiResult.ok(reportService.dashboardOverview());
    }
}
