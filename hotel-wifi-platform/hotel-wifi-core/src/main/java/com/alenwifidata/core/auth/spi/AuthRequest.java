package com.alenwifidata.core.auth.spi;

import lombok.Data;

/**
 * 认证请求
 */
@Data
public class AuthRequest {

    private Long tenantId;
    private Long hotelId;
    private String username;
    private String password;
    private String phone;
    private String verifyCode;
    private String wechatCode;
    private String cardNo;
    private String cardPassword;
    private String macAddress;
    private String ipAddress;
}
