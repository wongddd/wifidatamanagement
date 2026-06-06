package com.alenwifidata.core.payment;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.core.payment.monnify.MonnifyPaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付 Controller — Monnify 统一支付入口
 */
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final MonnifyPaymentService monnifyService;

    /** 创建 Monnify 支付订单 */
    @PostMapping("/monnify/create")
    public ApiResult<Map<String, Object>> createMonnifyOrder(@RequestBody MonnifyPayRequest request) {
        return ApiResult.ok(monnifyService.createOrder(
                request.getMemberId(), request.getPackageId(),
                request.getCustomerName(), request.getCustomerEmail()));
    }

    /** 查询交易状态 */
    @GetMapping("/monnify/status/{txRef}")
    public ApiResult<Map<String, String>> queryTransaction(@PathVariable String txRef) {
        return ApiResult.ok(monnifyService.verifyTransaction(txRef));
    }

    @Data
    public static class MonnifyPayRequest {
        private Long memberId; private Long packageId;
        private String customerName; private String customerEmail;
    }
}
