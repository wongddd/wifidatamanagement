package com.alenwifidata.core.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 短信服务（当前为日志模拟，生产环境对接阿里云短信/腾讯云短信）
 */
@Slf4j
@Service
public class SmsService {

    /**
     * 发送短信
     * @param phone 手机号
     * @param content 短信内容
     */
    public boolean send(String phone, String content) {
        if (phone == null || phone.isBlank()) {
            log.warn("短信发送跳过：手机号为空");
            return false;
        }
        // TODO: 对接阿里云短信 / 腾讯云短信 SDK
        log.info("【模拟短信】发送至 {}: {}", phone, content);
        return true;
    }

    /**
     * 发送验证码
     */
    public String sendVerifyCode(String phone) {
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        String content = "【WiFi管家】您的验证码是：" + code + "，5分钟内有效。";
        send(phone, content);
        return code; // 实际应当存入 Redis 设置5分钟过期
    }
}
