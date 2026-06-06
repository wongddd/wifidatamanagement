package com.alenwifidata.core.payment.monnify;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Monnify Webhook 回调控制器 — 不经过 JWT 认证
 *
 * 安全措施:
 * 1. IP 白名单: 仅接受来自 35.242.133.146 的请求
 * 2. HMAC-SHA512 签名验证
 * 3. 幂等处理: paymentReference 去重
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/monnify")
@RequiredArgsConstructor
public class MonnifyWebhookController {

    private final MonnifyPaymentService paymentService;
    private final MonnifyClient monnifyClient;

    private static final String MONNIFY_IP = "35.242.133.146";

    /** 支付完成回调 */
    @PostMapping("/notify")
    public ResponseEntity<String> handlePaymentNotification(
            @RequestBody String payload,
            @RequestHeader(value = "monnify-signature", required = false) String signature,
            HttpServletRequest request) {

        String remoteIp = getClientIp(request);

        // 1. IP 白名单校验
        if (!MONNIFY_IP.equals(remoteIp) && !"127.0.0.1".equals(remoteIp)) {
            log.warn("Monnify回调IP不匹配: expected={}, actual={}", MONNIFY_IP, remoteIp);
            return ResponseEntity.status(403).body("Forbidden");
        }

        // 2. 签名验证
        if (signature == null || signature.isEmpty()) {
            log.error("Monnify回调缺少签名头");
            return ResponseEntity.status(401).body("Missing signature");
        }
        if (!monnifyClient.verifySignature(payload, signature)) {
            log.error("Monnify回调签名验证失败");
            return ResponseEntity.status(401).body("Invalid signature");
        }

        // 3. 业务处理
        try {
            paymentService.handlePaymentWebhook(payload);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Monnify回调处理异常", e);
            return ResponseEntity.status(500).body("Error");
        }
    }

    /** 退款完成回调 */
    @PostMapping("/refund-notify")
    public ResponseEntity<String> handleRefundNotification(
            @RequestBody String payload,
            @RequestHeader(value = "monnify-signature", required = false) String signature,
            HttpServletRequest request) {

        String remoteIp = getClientIp(request);
        if (!MONNIFY_IP.equals(remoteIp) && !"127.0.0.1".equals(remoteIp)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        if (signature == null || !monnifyClient.verifySignature(payload, signature)) {
            return ResponseEntity.status(401).body("Invalid signature");
        }

        log.info("Monnify退款回调: {}", payload.substring(0, Math.min(200, payload.length())));
        return ResponseEntity.ok("OK");
    }

    /** 清算完成回调 */
    @PostMapping("/settlement-notify")
    public ResponseEntity<String> handleSettlementNotification(
            @RequestBody String payload,
            @RequestHeader(value = "monnify-signature", required = false) String signature,
            HttpServletRequest request) {

        String remoteIp = getClientIp(request);
        if (!MONNIFY_IP.equals(remoteIp) && !"127.0.0.1".equals(remoteIp)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        if (signature == null || !monnifyClient.verifySignature(payload, signature)) {
            return ResponseEntity.status(401).body("Invalid signature");
        }

        log.info("Monnify清算回调: {}", payload.substring(0, Math.min(200, payload.length())));
        return ResponseEntity.ok("OK");
    }

    /** 获取真实客户端 IP（考虑 Nginx 代理） */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能含多个IP（取第一个）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
