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

/**
 * 在线支付回调处理 — Monnify 统一支付
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BillingOrderMapper orderMapper;
    private final MemberMapper memberMapper;

    @Transactional
    public void completePayment(String orderNo, BigDecimal paidAmount, Long memberId) {
        BillingOrder order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BillingOrder>()
                        .eq(BillingOrder::getOrderNo, orderNo));

        if (order == null) throw new BusinessException(404, "订单不存在");
        if ("PAID".equals(order.getStatus())) { log.warn("重复支付: {}", orderNo); return; }

        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);

        Member member = memberMapper.selectById(memberId != null ? memberId : order.getMemberId());
        if (member != null) {
            member.setBalance(member.getBalance().add(paidAmount));
            memberMapper.updateById(member);
        }
        log.info("支付完成: orderNo={}, amount={}", orderNo, paidAmount);
    }
}
