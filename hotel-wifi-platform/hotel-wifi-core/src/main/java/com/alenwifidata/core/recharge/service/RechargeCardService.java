package com.alenwifidata.core.recharge.service;

import cn.hutool.core.util.RandomUtil;
import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.alenwifidata.core.package.mapper.BillingPackageMapper;
import com.alenwifidata.core.package.model.BillingPackage;
import com.alenwifidata.core.recharge.mapper.RechargeCardMapper;
import com.alenwifidata.core.recharge.model.RechargeCard;
import com.alenwifidata.core.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeCardService {

    private final RechargeCardMapper cardMapper;
    private final MemberMapper memberMapper;
    private final BillingPackageMapper packageMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    public Page<RechargeCard> page(int pageNum, int pageSize, String batchNo, String status) {
        Long tenantId = TenantContext.get();
        LambdaQueryWrapper<RechargeCard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RechargeCard::getTenantId, tenantId);
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.eq(RechargeCard::getBatchNo, batchNo);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(RechargeCard::getStatus, status);
        }
        wrapper.orderByDesc(RechargeCard::getCreatedAt);
        return cardMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional
    public List<RechargeCard> batchGenerate(int count, BigDecimal amount, Long packageId,
                                             LocalDateTime expireAt) {
        if (count < 1 || count > 10000) {
            throw new BusinessException(400, "生成数量需在 1-10000 之间");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "面值必须大于0");
        }

        Long tenantId = TenantContext.get();
        String batchNo = "RC" + System.currentTimeMillis();

        if (packageId != null) {
            BillingPackage pkg = packageMapper.selectById(packageId);
            if (pkg == null) {
                throw new BusinessException(404, "关联套餐不存在");
            }
        }

        List<RechargeCard> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RechargeCard card = new RechargeCard();
            card.setTenantId(tenantId);
            card.setBatchNo(batchNo);
            card.setCardNo(generateCardNo());
            card.setCardPassword(RandomUtil.randomString(8));
            card.setAmount(amount);
            card.setPackageId(packageId);
            card.setStatus("UNUSED");
            card.setExpireAt(expireAt);
            cards.add(card);
        }

        // 分批插入（每批500条）
        for (int i = 0; i < cards.size(); i += 500) {
            int end = Math.min(i + 500, cards.size());
            cardMapper.batchInsert(cards.subList(i, end));
        }

        log.info("批量生成充值卡完成: batchNo={}, count={}, amount={}", batchNo, count, amount);
        return cards;
    }

    /**
     * 核销充值卡
     */
    @Transactional
    public Member redeem(Long tenantId, String cardNo, String password, Long memberId) {
        RechargeCard card = cardMapper.selectByCardNo(tenantId, cardNo);
        if (card == null) {
            throw new BusinessException(404, "充值卡不存在");
        }
        if (!"UNUSED".equals(card.getStatus())) {
            throw new BusinessException(400, "充值卡已使用或已过期");
        }
        if (!card.getCardPassword().equals(password)) {
            throw new BusinessException(400, "充值卡密码错误");
        }
        if (card.getExpireAt() != null && card.getExpireAt().isBefore(LocalDateTime.now())) {
            card.setStatus("EXPIRED");
            cardMapper.updateById(card);
            throw new BusinessException(400, "充值卡已过期");
        }

        Member member = memberMapper.selectById(memberId);
        if (member == null || member.getStatus() != 1) {
            throw new BusinessException(400, "会员不存在或已停用");
        }

        // 更新卡状态
        card.setStatus("USED");
        card.setUsedBy(memberId);
        card.setUsedAt(LocalDateTime.now());
        cardMapper.updateById(card);

        // 充值到会员账户
        if (card.getPackageId() != null) {
            // 套餐卡：直接激活套餐
            BillingPackage pkg = packageMapper.selectById(card.getPackageId());
            if (pkg != null) {
                if ("TIME".equals(pkg.getBillingType()) || "HYBRID".equals(pkg.getBillingType())) {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime newExpire = member.getExpireAt() != null && member.getExpireAt().isAfter(now)
                            ? member.getExpireAt().plusSeconds(pkg.getDurationSeconds())
                            : now.plusSeconds(pkg.getDurationSeconds());
                    member.setExpireAt(newExpire);
                }
                member.setBalance(member.getBalance().add(pkg.getPrice()));
            }
        } else {
            // 余额卡：充值金额
            member.setBalance(member.getBalance().add(card.getAmount()));
        }
        memberMapper.updateById(member);

        log.info("充值卡核销成功: cardNo={}, memberId={}, amount={}", cardNo, memberId, card.getAmount());
        return member;
    }

    /**
     * 作废充值卡
     */
    public void revoke(Long id) {
        RechargeCard card = cardMapper.selectById(id);
        if (card == null) {
            throw new BusinessException(404, "充值卡不存在");
        }
        if (!"UNUSED".equals(card.getStatus())) {
            throw new BusinessException(400, "只能作废未使用的充值卡");
        }
        card.setStatus("REVOKED");
        cardMapper.updateById(card);
    }

    private String generateCardNo() {
        long timestamp = System.currentTimeMillis();
        int random = RANDOM.nextInt(900000) + 100000;
        return String.format("HW%s%06d", Long.toString(timestamp, 36).toUpperCase().substring(4), random);
    }
}
