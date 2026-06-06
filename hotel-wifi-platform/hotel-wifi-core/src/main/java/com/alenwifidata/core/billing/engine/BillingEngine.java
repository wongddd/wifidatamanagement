package com.alenwifidata.core.billing.engine;

import com.alenwifidata.common.constant.SystemConstants;
import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.billing.mapper.BillingOrderMapper;
import com.alenwifidata.core.billing.mapper.OnlineSessionMapper;
import com.alenwifidata.core.billing.model.BillingDeduction;
import com.alenwifidata.core.billing.model.BillingOrder;
import com.alenwifidata.core.billing.model.OnlineSession;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.alenwifidata.core.billingpackage.mapper.BillingPackageMapper;
import com.alenwifidata.core.billingpackage.model.BillingPackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 计费引擎 —— 核心扣费逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingEngine {

    private final MemberMapper memberMapper;
    private final BillingOrderMapper orderMapper;
    private final BillingPackageMapper packageMapper;
    private final OnlineSessionMapper sessionMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> billingDeductScript;

    /**
     * 创建订单（会员购买套餐）
     */
    @Transactional
    public BillingOrder createOrder(Long tenantId, Long hotelId, Long memberId, Long packageId,
                                     String payType) {
        Member member = memberMapper.selectById(memberId);
        BillingPackage pkg = packageMapper.selectById(packageId);

        if (member == null || member.getStatus() != 1) {
            throw new BusinessException(400, "会员不存在或已停用");
        }
        if (pkg == null || pkg.getStatus() != 1) {
            throw new BusinessException(400, "套餐不存在或已停用");
        }

        // 余额支付：检查余额
        if ("BALANCE".equals(payType)) {
            if (member.getBalance().compareTo(pkg.getPrice()) < 0) {
                throw new BusinessException(402, "余额不足");
            }
            member.setBalance(member.getBalance().subtract(pkg.getPrice()));
            memberMapper.updateById(member);
        }

        // 创建订单
        BillingOrder order = new BillingOrder();
        order.setOrderNo(generateOrderNo());
        order.setTenantId(tenantId);
        order.setHotelId(hotelId);
        order.setMemberId(memberId);
        order.setPackageId(packageId);
        order.setAmount(pkg.getPrice());
        order.setPayType(payType);
        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        orderMapper.insert(order);

        // 更新会员到期时间（包时套餐）
        if ("TIME".equals(pkg.getBillingType()) || "HYBRID".equals(pkg.getBillingType())) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime newExpire;
            if (member.getExpireAt() != null && member.getExpireAt().isAfter(now)) {
                newExpire = member.getExpireAt().plusSeconds(pkg.getDurationSeconds());
            } else {
                newExpire = now.plusSeconds(pkg.getDurationSeconds());
            }
            member.setExpireAt(newExpire);
            memberMapper.updateById(member);
        }

        log.info("订单创建成功: orderNo={}, memberId={}, packageId={}, amount={}",
                order.getOrderNo(), memberId, packageId, pkg.getPrice());

        return order;
    }

    /**
     * 开始上网会话
     */
    @Transactional
    public OnlineSession startSession(Long tenantId, Long hotelId, Long memberId,
                                       Long routerId, String macAddress, String ipAddress,
                                       Long packageId) {
        // 检查会员是否已有活跃会话
        List<OnlineSession> activeList = sessionMapper.selectActiveSessions(tenantId);
        long memberActiveCount = activeList.stream()
                .filter(s -> s.getMemberId().equals(memberId))
                .count();

        BillingPackage pkg = packageMapper.selectById(packageId);
        if (pkg != null && memberActiveCount >= pkg.getMaxDevices()) {
            throw new BusinessException(429, "已达到最大同时在线设备数");
        }

        // 预扣首段费用到 Redis
        String balanceKey = SystemConstants.REDIS_BALANCE_PREFIX + tenantId + ":" + memberId;
        Member member = memberMapper.selectById(memberId);
        redisTemplate.opsForValue().setIfAbsent(balanceKey, member.getBalance().doubleValue());

        // 创建会话
        OnlineSession session = new OnlineSession();
        session.setTenantId(tenantId);
        session.setHotelId(hotelId);
        session.setMemberId(memberId);
        session.setRouterId(routerId);
        session.setMacAddress(macAddress);
        session.setIpAddress(ipAddress);
        session.setPackageId(packageId);
        session.setLoginAt(LocalDateTime.now());
        session.setTotalBytesIn(0L);
        session.setTotalBytesOut(0L);
        session.setTotalCost(BigDecimal.ZERO);
        session.setStatus("ACTIVE");
        sessionMapper.insert(session);

        log.info("上网会话开始: sessionId={}, memberId={}, routerId={}, ip={}", session.getId(), memberId, routerId, ipAddress);
        return session;
    }

    /**
     * 结束上网会话并结算
     */
    @Transactional
    public OnlineSession endSession(Long sessionId, String reason) {
        OnlineSession session = sessionMapper.selectById(sessionId);
        if (session == null || !"ACTIVE".equals(session.getStatus())) {
            throw new BusinessException(404, "会话不存在或已结束");
        }

        session.setLogoutAt(LocalDateTime.now());
        session.setStatus(reason != null ? reason : "FINISHED");
        session.setLogoutReason(reason);
        sessionMapper.updateById(session);

        // 同步 Redis 余额回 MySQL
        String balanceKey = SystemConstants.REDIS_BALANCE_PREFIX + session.getTenantId() + ":" + session.getMemberId();
        Object redisBalance = redisTemplate.opsForValue().get(balanceKey);
        if (redisBalance != null) {
            Member member = memberMapper.selectById(session.getMemberId());
            member.setBalance(BigDecimal.valueOf(((Number) redisBalance).doubleValue()));
            memberMapper.updateById(member);
            redisTemplate.delete(balanceKey);
        }

        log.info("上网会话结束: sessionId={}, reason={}, totalCost={}", sessionId, reason, session.getTotalCost());
        return session;
    }

    /**
     * 实时扣费（由流量采集调度器调用）
     */
    @Transactional
    public void deductTrafficFee(Long sessionId, long newBytesIn, long newBytesOut) {
        OnlineSession session = sessionMapper.selectById(sessionId);
        if (session == null || !"ACTIVE".equals(session.getStatus())) {
            return;
        }

        BillingPackage pkg = packageMapper.selectById(session.getPackageId());
        if (pkg == null || !"TRAFFIC".equals(pkg.getBillingType()) && !"HYBRID".equals(pkg.getBillingType())) {
            // 包时套餐不按流量扣费，只更新流量记录
            session.setTotalBytesIn(newBytesIn);
            session.setTotalBytesOut(newBytesOut);
            sessionMapper.updateById(session);
            return;
        }

        // 计算流量增量
        long bytesDeltaIn = newBytesIn - session.getTotalBytesIn();
        long bytesDeltaOut = newBytesOut - session.getTotalBytesOut();
        long bytesTotalDelta = bytesDeltaIn + bytesDeltaOut;

        if (bytesTotalDelta <= 0) {
            return;
        }

        // 计算费用：按套餐价格换算每 MB 单价
        // 如果套餐有流量配额，按配额算单价；否则按 1GB=15元 默认单价
        BigDecimal pricePerMB;
        if (pkg.getTrafficBytes() > 0) {
            pricePerMB = pkg.getPrice()
                    .divide(BigDecimal.valueOf(pkg.getTrafficBytes()).divide(BigDecimal.valueOf(SystemConstants.MB), 2, RoundingMode.HALF_UP), 4, RoundingMode.HALF_UP);
        } else {
            pricePerMB = new BigDecimal("0.015"); // 默认 1MB=0.015元
        }

        BigDecimal bytesDelta = BigDecimal.valueOf(bytesTotalDelta);
        BigDecimal mbDelta = bytesDelta.divide(BigDecimal.valueOf(SystemConstants.MB), 4, RoundingMode.HALF_UP);
        BigDecimal deductAmount = mbDelta.multiply(pricePerMB).setScale(4, RoundingMode.HALF_UP);

        // Redis 原子扣费
        String balanceKey = SystemConstants.REDIS_BALANCE_PREFIX + session.getTenantId() + ":" + session.getMemberId();
        Long remaining = redisTemplate.execute(
                billingDeductScript,
                Collections.singletonList(balanceKey),
                String.valueOf(deductAmount.doubleValue())
        );

        if (remaining == null || remaining < 0) {
            // 余额不足，踢下线
            log.warn("会员 {} 余额不足，强制下线 sessionId={}", session.getMemberId(), sessionId);
            endSession(sessionId, "KICKED");
            // TODO: 调用 MikroTik API 踢下线
            return;
        }

        // 更新会话流量和费用
        session.setTotalBytesIn(newBytesIn);
        session.setTotalBytesOut(newBytesOut);
        session.setTotalCost(session.getTotalCost().add(deductAmount));
        sessionMapper.updateById(session);

        // 记录扣费明细
        // TODO: 写入 billing_deduction 表

        log.debug("扣费成功: sessionId={}, bytesDelta={}, amount={}, remaining={}",
                sessionId, bytesTotalDelta, deductAmount, remaining);
    }

    /**
     * 强制踢下线
     */
    public void kickSession(Long sessionId) {
        endSession(sessionId, "KICKED");
    }

    private String generateOrderNo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
