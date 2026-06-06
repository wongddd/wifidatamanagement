package com.alenwifidata.core.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * WhatsApp 消息服务 — 通过 WhatsApp Cloud API 发送验证码和通知
 *
 * WhatsApp Cloud API: POST https://graph.facebook.com/v19.0/{phone-number-id}/messages
 * 需要: Meta 开发者账号 + WhatsApp Business App + 验证企业
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    /**
     * 发送 WhatsApp 消息
     * @param phone 手机号（国际格式，如:+8613800138000）
     * @param content 消息内容
     */
    public boolean send(String phone, String content) {
        if (phone == null || phone.isBlank()) {
            log.warn("WhatsApp发送跳过：手机号为空");
            return false;
        }
        // 格式化号码：去掉空格和加号，确保国际格式
        String formattedPhone = phone.replaceAll("[\\s\\-()]", "");
        if (!formattedPhone.startsWith("+")) {
            formattedPhone = "+" + formattedPhone;
        }

        // TODO: 对接 WhatsApp Cloud API
        // POST https://graph.facebook.com/v19.0/{phone-number-id}/messages
        // Authorization: Bearer {whatsapp_token}
        // Body: {
        //   "messaging_product": "whatsapp",
        //   "to": "{phone}",
        //   "type": "text",
        //   "text": { "body": "{content}" }
        // }

        log.info("【WhatsApp模拟】发送至 {}: {}", formattedPhone, content);
        return true;
    }

    /**
     * 发送验证码
     * @param phone 手机号
     * @return 验证码（存入 Redis 5分钟过期后供校验）
     */
    public String sendVerifyCode(String phone) {
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        String content = "*WiFi管家* 您的验证码是: *" + code + "*\n5分钟内有效。";
        send(phone, content);
        // TODO: 将 code 存入 Redis: SETEX "whatsapp:code:{phone}" 300 {code}
        return code;
    }
}
