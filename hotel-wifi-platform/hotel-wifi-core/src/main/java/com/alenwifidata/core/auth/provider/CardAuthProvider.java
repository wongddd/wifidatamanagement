package com.alenwifidata.core.auth.provider;

import com.alenwifidata.common.enums.AuthType;
import com.alenwifidata.core.auth.spi.AuthProvider;
import com.alenwifidata.core.auth.spi.AuthRequest;
import com.alenwifidata.core.auth.spi.AuthResult;
import com.alenwifidata.core.recharge.mapper.RechargeCardMapper;
import com.alenwifidata.core.recharge.model.RechargeCard;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 充值卡认证 — 住客可直接用充值卡上网
 */
@Slf4j
@Component("cardAuthProvider")
@RequiredArgsConstructor
public class CardAuthProvider implements AuthProvider {

    private final RechargeCardMapper cardMapper;
    private final MemberMapper memberMapper;

    @Override
    public AuthResult authenticate(AuthRequest request) {
        if (request.getCardNo() == null || request.getCardPassword() == null) {
            return AuthResult.fail("卡号和密码不能为空");
        }

        // 查找充值卡
        RechargeCard card = cardMapper.selectByCardNo(request.getTenantId(), request.getCardNo());
        if (card == null) {
            return AuthResult.fail("充值卡不存在");
        }
        if (!"UNUSED".equals(card.getStatus())) {
            return AuthResult.fail("充值卡已使用或已过期");
        }
        if (!card.getCardPassword().equals(request.getCardPassword())) {
            return AuthResult.fail("充值卡密码错误");
        }
        if (card.getExpireAt() != null && card.getExpireAt().isBefore(LocalDateTime.now())) {
            return AuthResult.fail("充值卡已过期");
        }

        // 自动创建或查找会员
        Member member = null;
        if (request.getPhone() != null) {
            member = memberMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                            .eq(Member::getTenantId, request.getTenantId())
                            .eq(Member::getPhone, request.getPhone())
            );
        }

        if (member == null) {
            // 自动注册：手机号即账号（如果没有手机号，用卡号生成临时账号）
            String username = request.getPhone() != null ? request.getPhone() : "CARD_" + card.getCardNo().substring(0, 8);
            member = new Member();
            member.setTenantId(request.getTenantId());
            member.setHotelId(request.getHotelId());
            member.setUsername(username);
            member.setPhone(request.getPhone());
            member.setPassword("");
            memberMapper.insert(member);
            log.info("充值卡认证自动创建会员: {}", username);
        }

        log.info("充值卡认证通过: cardNo={}, memberId={}", card.getCardNo(), member.getId());
        return AuthResult.success(String.valueOf(member.getId()), member.getUsername());
    }

    @Override
    public AuthType getType() {
        return AuthType.CARD;
    }

    @Override
    public boolean supports(AuthType type) {
        return AuthType.CARD == type;
    }
}
