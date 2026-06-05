package com.alenwifidata.core.auth.provider;

import com.alenwifidata.common.enums.AuthType;
import com.alenwifidata.core.auth.spi.AuthProvider;
import com.alenwifidata.core.auth.spi.AuthResult;
import com.alenwifidata.core.auth.spi.AuthRequest;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.alenwifidata.core.notification.service.SmsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 短信验证码认证
 */
@Component("smsAuthProvider")
@RequiredArgsConstructor
public class SmsAuthProvider implements AuthProvider {

    private final MemberMapper memberMapper;
    private final SmsService smsService;

    @Override
    public AuthResult authenticate(AuthRequest request) {
        String phone = request.getPhone();
        String code = request.getVerifyCode();

        if (phone == null || code == null) {
            return AuthResult.fail("手机号和验证码不能为空");
        }

        // TODO: 从 Redis 中校验验证码（当前简化处理）
        // String savedCode = redisTemplate.opsForValue().get("sms:code:" + phone);

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
            member.setPassword(""); // 短信认证无需密码
            member.setRealName(phone);
            memberMapper.insert(member);
        }

        return AuthResult.success(String.valueOf(member.getId()), member.getUsername());
    }

    @Override
    public AuthType getType() {
        return AuthType.SMS;
    }

    @Override
    public boolean supports(AuthType type) {
        return AuthType.SMS == type;
    }
}
