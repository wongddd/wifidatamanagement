package com.alenwifidata.core.auth.spi;

import com.alenwifidata.common.enums.AuthType;

/**
 * 认证提供者 SPI 接口
 */
public interface AuthProvider {

    AuthResult authenticate(AuthRequest request);

    AuthType getType();

    boolean supports(AuthType type);
}
