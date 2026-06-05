package com.alenwifidata.core.billing.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.core.billing.engine.BillingEngine;
import com.alenwifidata.core.billing.mapper.OnlineSessionMapper;
import com.alenwifidata.core.billing.model.BillingOrder;
import com.alenwifidata.core.billing.model.OnlineSession;
import com.alenwifidata.core.tenant.TenantContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingEngine billingEngine;
    private final OnlineSessionMapper sessionMapper;

    /**
     * 创建订单（购买套餐）
     */
    @PostMapping("/orders")
    public ApiResult<BillingOrder> createOrder(@RequestBody CreateOrderRequest request) {
        Long tenantId = TenantContext.get();
        BillingOrder order = billingEngine.createOrder(
                tenantId,
                request.getHotelId(),
                request.getMemberId(),
                request.getPackageId(),
                request.getPayType()
        );
        return ApiResult.ok(order);
    }

    /**
     * 开始上网会话
     */
    @PostMapping("/sessions/start")
    public ApiResult<OnlineSession> startSession(@RequestBody StartSessionRequest request) {
        Long tenantId = TenantContext.get();
        OnlineSession session = billingEngine.startSession(
                tenantId,
                request.getHotelId(),
                request.getMemberId(),
                request.getRouterId(),
                request.getMacAddress(),
                request.getIpAddress(),
                request.getPackageId()
        );
        return ApiResult.ok(session);
    }

    /**
     * 获取当前在线用户
     */
    @GetMapping("/sessions/active")
    public ApiResult<List<OnlineSession>> activeSessions() {
        Long tenantId = TenantContext.get();
        return ApiResult.ok(sessionMapper.selectActiveSessions(tenantId));
    }

    /**
     * 强制踢下线
     */
    @PostMapping("/sessions/{id}/kick")
    public ApiResult<Map<String, Object>> kickSession(@PathVariable Long id) {
        billingEngine.kickSession(id);
        return ApiResult.ok(Map.of("success", true, "sessionId", id));
    }

    @Data
    public static class CreateOrderRequest {
        private Long hotelId;
        private Long memberId;
        private Long packageId;
        private String payType = "BALANCE";
    }

    @Data
    public static class StartSessionRequest {
        private Long hotelId;
        private Long memberId;
        private Long routerId;
        private String macAddress;
        private String ipAddress;
        private Long packageId;
    }
}
