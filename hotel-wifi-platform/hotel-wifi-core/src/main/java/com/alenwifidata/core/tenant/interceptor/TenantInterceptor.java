package com.alenwifidata.core.tenant.interceptor;

import com.alenwifidata.core.tenant.TenantContext;
import com.alenwifidata.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.alenwifidata.common.constant.SystemConstants.TOKEN_HEADER;
import static com.alenwifidata.common.constant.SystemConstants.TOKEN_PREFIX;

/**
 * 租户拦截器：从 JWT Token 解析租户ID并设置到 ThreadLocal
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(TOKEN_HEADER);
        if (StringUtils.hasText(token) && token.startsWith(TOKEN_PREFIX)) {
            token = token.substring(TOKEN_PREFIX.length());
            if (jwtUtil.validateToken(token)) {
                Long tenantId = jwtUtil.getTenantId(token);
                TenantContext.set(tenantId);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
