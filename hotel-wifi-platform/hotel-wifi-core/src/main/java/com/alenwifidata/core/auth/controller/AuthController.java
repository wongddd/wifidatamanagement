package com.alenwifidata.core.auth.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.common.util.JwtUtil;
import com.alenwifidata.core.tenant.mapper.SysUserMapper;
import com.alenwifidata.core.tenant.model.SysUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody LoginRequest request) {
        // 查询用户（tenantId 从请求中获取）
        SysUser user = sysUserMapper.selectByTenantAndUsername(
                request.getTenantId(), request.getUsername());

        if (user == null || user.getStatus() != 1) {
            return ApiResult.fail(401, "用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ApiResult.fail(401, "用户名或密码错误");
        }

        // 生成 Token
        String token = jwtUtil.generateToken(
                user.getId(), user.getTenantId(), user.getUsername(), user.getRole());

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("tenantId", user.getTenantId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("role", user.getRole());

        return ApiResult.ok(result);
    }

    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        var claims = jwtUtil.parseToken(token);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", claims.getSubject());
        result.put("tenantId", claims.get("tenantId"));
        result.put("username", claims.get("username"));
        result.put("role", claims.get("role"));
        return ApiResult.ok(result);
    }

    @Data
    public static class LoginRequest {
        private Long tenantId = 1L;
        private String username;
        private String password;
    }
}
