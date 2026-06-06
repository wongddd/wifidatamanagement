package com.alenwifidata.core.auth.provider;

import com.alenwifidata.common.enums.AuthType;
import com.alenwifidata.core.auth.spi.AuthProvider;
import com.alenwifidata.core.auth.spi.AuthRequest;
import com.alenwifidata.core.auth.spi.AuthResult;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 用户名密码认证 — 住客 Portal 最常用认证方式
 */
@Slf4j
@Component("usernamePasswordAuth")
@RequiredArgsConstructor
public class UsernamePasswordAuthProvider implements AuthProvider {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResult authenticate(AuthRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return AuthResult.fail("用户名和密码不能为空");
        }

        // 查询会员
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getTenantId, request.getTenantId())
               .and(w -> w.eq(Member::getUsername, request.getUsername())
                         .or().eq(Member::getPhone, request.getUsername()));

        Member member = memberMapper.selectOne(wrapper);

        if (member == null) {
            return AuthResult.fail("用户名或密码错误");
        }
        if (member.getStatus() != 1) {
            return AuthResult.fail("账号已停用，请联系前台");
        }
        if (member.getExpireAt() != null && member.getExpireAt().isBefore(java.time.LocalDateTime.now())) {
            return AuthResult.fail("套餐已过期，请续费");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            return AuthResult.fail("用户名或密码错误");
        }

        log.info("Portal账号认证通过: username={}, memberId={}", member.getUsername(), member.getId());
        return AuthResult.success(String.valueOf(member.getId()), member.getUsername());
    }

    @Override
    public AuthType getType() {
        return AuthType.USERNAME_PASSWORD;
    }

    @Override
    public boolean supports(AuthType type) {
        return AuthType.USERNAME_PASSWORD == type;
    }
}
