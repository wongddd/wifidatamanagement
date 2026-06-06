package com.alenwifidata.core.auth.provider;

import com.alenwifidata.common.enums.AuthType;
import com.alenwifidata.core.auth.spi.AuthProvider;
import com.alenwifidata.core.auth.spi.AuthResult;
import com.alenwifidata.core.auth.spi.AuthRequest;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * WhatsApp 验证码认证
 */
@Slf4j
@Component("whatsappAuthProvider")
@RequiredArgsConstructor
public class WhatsAppAuthProvider implements AuthProvider {

    private final MemberMapper memberMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public AuthResult authenticate(AuthRequest request) {
        String phone = request.getPhone();
        String code = request.getVerifyCode();

        if (phone == null || code == null) {
            return AuthResult.fail("手机号和验证码不能为空");
        }

        // 从 Redis 校验验证码
        String redisKey = "whatsapp:code:" + phone;
        Object savedCode = redisTemplate.opsForValue().get(redisKey);
        if (savedCode == null) {
            return AuthResult.fail("验证码已过期，请重新获取");
        }
        if (!savedCode.toString().equals(code)) {
            return AuthResult.fail("验证码错误");
        }
        // 验证通过后删除
        redisTemplate.delete(redisKey);

        // 查询或创建会员
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getPhone, phone);
        Member member = memberMapper.selectOne(wrapper);

        if (member == null) {
            // 自动注册：手机号即账号
            member = new Member();
            member.setTenantId(request.getTenantId());
            member.setHotelId(request.getHotelId());
            member.setUsername(phone);
            member.setPhone(phone);
            member.setPassword("");
            member.setRealName(phone);
            memberMapper.insert(member);
        }

        log.info("WhatsApp认证通过: phone={}, memberId={}", phone, member.getId());
        return AuthResult.success(String.valueOf(member.getId()), member.getUsername());
    }

    @Override
    public AuthType getType() {
        return AuthType.WHATSAPP;
    }

    @Override
    public boolean supports(AuthType type) {
        return AuthType.WHATSAPP == type;
    }
}
