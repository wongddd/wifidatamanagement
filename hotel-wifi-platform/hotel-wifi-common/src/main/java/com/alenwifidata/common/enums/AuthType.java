package com.alenwifidata.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 认证方式
 */
@Getter
@AllArgsConstructor
public enum AuthType {

    /** 用户名密码 */
    USERNAME_PASSWORD("账号密码"),
    /** 微信扫码 */
    WECHAT("微信认证"),
    /** 短信验证码 */
    SMS("短信认证"),
    /** 充值卡 */
    CARD("充值卡");

    private final String description;
}
