package com.alenwifidata.core.member.service;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.alenwifidata.core.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public Page<Member> page(int pageNum, int pageSize, String keyword, Integer status) {
        Long tenantId = TenantContext.get();
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getTenantId, tenantId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Member::getUsername, keyword)
                             .or().like(Member::getRealName, keyword)
                             .or().like(Member::getPhone, keyword));
        }
        if (status != null) {
            wrapper.eq(Member::getStatus, status);
        }
        wrapper.orderByDesc(Member::getCreatedAt);
        return memberMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Member getById(Long id) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new BusinessException(404, "会员不存在");
        }
        return member;
    }

    public Member create(Member member) {
        member.setTenantId(TenantContext.get());
        member.setPassword(passwordEncoder.encode(member.getPassword()));
        member.setBalance(BigDecimal.ZERO);
        member.setPoints(0);
        member.setStatus(1);
        memberMapper.insert(member);
        return member;
    }

    public Member update(Member member) {
        Member existing = getById(member.getId());
        // 不更新密码和余额
        member.setPassword(null);
        member.setBalance(null);
        memberMapper.updateById(member);
        return getById(member.getId());
    }

    @Transactional
    public Member recharge(Long id, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "充值金额必须大于0");
        }
        Member member = getById(id);
        member.setBalance(member.getBalance().add(amount));
        memberMapper.updateById(member);
        return member;
    }

    public void updateStatus(Long id, Integer status) {
        Member member = getById(id);
        member.setStatus(status);
        memberMapper.updateById(member);
    }

    public void updateExpiry(Long id, LocalDateTime expireAt) {
        Member member = getById(id);
        member.setExpireAt(expireAt);
        memberMapper.updateById(member);
    }
}
