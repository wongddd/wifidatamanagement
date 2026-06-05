package com.alenwifidata.core.payment;

import com.alenwifidata.common.dto.ApiResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付 Controller — 微信/支付宝支付接口
 */
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 创建微信支付订单 */
    @PostMapping("/wechat/create")
    public ApiResult<Map<String, Object>> createWechat(@RequestBody PayRequest request) {
        return ApiResult.ok(paymentService.createWechatOrder(
                request.getTenantId(), request.getMemberId(),
                request.getPackageId(), request.getAmount(),
                request.getOpenId()
        ));
    }

    /** 创建支付宝支付订单 */
    @PostMapping("/alipay/create")
    public ApiResult<Map<String, Object>> createAlipay(@RequestBody PayRequest request) {
        return ApiResult.ok(paymentService.createAlipayOrder(
                request.getTenantId(), request.getMemberId(),
                request.getPackageId(), request.getAmount()
        ));
    }

    /** 微信支付回调（需在白名单中开放，不走JWT认证） */
    @PostMapping("/wechat/notify")
    public String wechatNotify(@RequestBody String xmlBody) {
        // TODO: 解析 XML，验签，提取 orderNo/transactionId/amount
        paymentService.handleWechatNotify("MOCK_ORDER", "MOCK_TXN", BigDecimal.ZERO);
        return "<xml><return_code>SUCCESS</return_code></xml>";
    }

    /** 支付宝支付回调 */
    @PostMapping("/alipay/notify")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        // TODO: 验签，提取 out_trade_no/trade_no/total_amount
        paymentService.handleAlipayNotify("MOCK_ORDER", "MOCK_TXN", BigDecimal.ZERO);
        return "success";
    }

    @Data
    public static class PayRequest {
        private Long tenantId;
        private Long memberId;
        private Long packageId;
        private BigDecimal amount;
        private String openId;   // 微信 OpenID
    }
}
