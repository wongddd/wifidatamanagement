package com.alenwifidata.core.payment;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.core.payment.monnify.MonnifyPaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付 Controller — Monnify / 微信 / 支付宝 统一入口
 */
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final MonnifyPaymentService monnifyService;

    // ===== Monnify 支付 =====

    /** 创建 Monnify 支付订单（住客 Portal 调用） */
    @PostMapping("/monnify/create")
    public ApiResult<Map<String, Object>> createMonnifyOrder(@RequestBody MonnifyPayRequest request) {
        return ApiResult.ok(monnifyService.createOrder(
                request.getMemberId(), request.getPackageId(),
                request.getCustomerName(), request.getCustomerEmail()
        ));
    }

    /** 查询 Monnify 交易状态 */
    @GetMapping("/monnify/status/{txRef}")
    public ApiResult<Map<String, String>> queryMonnifyTransaction(@PathVariable String txRef) {
        return ApiResult.ok(monnifyService.verifyTransaction(txRef));
    }

    // ===== 微信支付 =====

    @PostMapping("/wechat/create")
    public ApiResult<Map<String, Object>> createWechat(@RequestBody PayRequest request) {
        return ApiResult.ok(paymentService.createWechatOrder(
                request.getTenantId(), request.getMemberId(),
                request.getPackageId(), request.getAmount(),
                request.getOpenId()
        ));
    }

    @PostMapping("/wechat/notify")
    public String wechatNotify(@RequestBody String xmlBody) {
        paymentService.handleWechatNotify("MOCK_ORDER", "MOCK_TXN", BigDecimal.ZERO);
        return "<xml><return_code>SUCCESS</return_code></xml>";
    }

    // ===== 支付宝 =====

    @PostMapping("/alipay/create")
    public ApiResult<Map<String, Object>> createAlipay(@RequestBody PayRequest request) {
        return ApiResult.ok(paymentService.createAlipayOrder(
                request.getTenantId(), request.getMemberId(),
                request.getPackageId(), request.getAmount()
        ));
    }

    @PostMapping("/alipay/notify")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        paymentService.handleAlipayNotify("MOCK_ORDER", "MOCK_TXN", BigDecimal.ZERO);
        return "success";
    }

    // ===== DTO =====

    @Data
    public static class MonnifyPayRequest {
        private Long memberId;
        private Long packageId;
        private String customerName;
        private String customerEmail;
    }

    @Data
    public static class PayRequest {
        private Long tenantId;
        private Long memberId;
        private Long packageId;
        private BigDecimal amount;
        private String openId;
    }
}
