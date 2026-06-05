package com.alenwifidata.core.payment;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.billing.mapper.BillingOrderMapper;
import com.alenwifidata.core.billing.model.BillingOrder;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 在线支付服务 — 微信支付 / 支付宝
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BillingOrderMapper orderMapper;
    private final MemberMapper memberMapper;

    /**
     * 创建微信支付预支付订单
     *
     * 流程:
     * 1. 创建本地订单 (status=PENDING)
     * 2. 调用微信统一下单 API → 获取 prepay_id
     * 3. 返回 JSAPI 参数给前端调起支付
     */
    public Map<String, Object> createWechatOrder(Long tenantId, Long memberId,
                                                   Long packageId, BigDecimal amount,
                                                   String openId) {
        // 1. 创建本地预支付订单
        BillingOrder order = new BillingOrder();
        order.setOrderNo("WX" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
        order.setTenantId(tenantId);
        order.setMemberId(memberId);
        order.setPackageId(packageId);
        order.setAmount(amount);
        order.setPayType("WECHAT");
        order.setStatus("PENDING");
        orderMapper.insert(order);

        // 2. TODO: 调用微信支付 API v3 JSAPI 下单
        // WechatPayClient.createJsapiOrder(order.getOrderNo(), amount, openId, notifyUrl);
        // 返回 prepay_id, nonceStr, timeStamp, signType, paySign

        String prepayId = "wx_prepay_" + order.getOrderNo(); // 模拟

        log.info("微信预支付订单创建: orderNo={}, amount={}", order.getOrderNo(), amount);

        return Map.of(
                "orderNo", order.getOrderNo(),
                "prepayId", prepayId,
                "amount", amount
        );
    }

    /**
     * 创建支付宝预支付订单
     */
    public Map<String, Object> createAlipayOrder(Long tenantId, Long memberId,
                                                   Long packageId, BigDecimal amount) {
        BillingOrder order = new BillingOrder();
        order.setOrderNo("ALI" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
        order.setTenantId(tenantId);
        order.setMemberId(memberId);
        order.setPackageId(packageId);
        order.setAmount(amount);
        order.setPayType("ALIPAY");
        order.setStatus("PENDING");
        orderMapper.insert(order);

        // TODO: 调用支付宝 SDK pageExecute / commonExecute
        // AlipayTradePrecreateResponse response = alipayClient.execute(request);
        // return response.getQrCode(); // 当面付二维码

        String qrCode = "https://qr.alipay.com/" + order.getOrderNo(); // 模拟
        log.info("支付宝预支付订单创建: orderNo={}, amount={}", order.getOrderNo(), amount);

        return Map.of(
                "orderNo", order.getOrderNo(),
                "qrCode", qrCode,
                "amount", amount
        );
    }

    /**
     * 微信支付回调处理
     */
    @Transactional
    public void handleWechatNotify(String orderNo, String transactionId, BigDecimal paidAmount) {
        log.info("微信支付回调: orderNo={}, transactionId={}, amount={}", orderNo, transactionId, paidAmount);

        BillingOrder order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BillingOrder>()
                        .eq(BillingOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException(404, "订单不存在: " + orderNo);
        }
        if ("PAID".equals(order.getStatus())) {
            log.warn("重复支付通知: {}", orderNo);
            return;
        }

        // 更新订单状态
        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 充值到会员账户
        Member member = memberMapper.selectById(order.getMemberId());
        if (member != null) {
            member.setBalance(member.getBalance().add(paidAmount));
            memberMapper.updateById(member);
        }

        log.info("微信支付处理完成: orderNo={}, memberId={}", orderNo, order.getMemberId());
    }

    /**
     * 支付宝支付回调处理
     */
    @Transactional
    public void handleAlipayNotify(String orderNo, String tradeNo, BigDecimal paidAmount) {
        log.info("支付宝回调: orderNo={}, tradeNo={}, amount={}", orderNo, tradeNo, paidAmount);
        handleWechatNotify(orderNo, tradeNo, paidAmount); // 逻辑相同
    }
}
