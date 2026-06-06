package com.alenwifidata.core.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * WhatsApp 消息服务 — 通过 WhatsApp Cloud API 发送验证码和通知
 *
 * WhatsApp Cloud API: POST https://graph.facebook.com/v21.0/{phone-number-id}/messages
 * 需要: Meta 开发者账号 + WhatsApp Business App + Webhook 验证
 * 文档: https://developers.facebook.com/docs/whatsapp/cloud-api
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    @Value("${whatsapp.api-url:https://graph.facebook.com}")
    private String apiUrl;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private OkHttpClient httpClient;

    @PostConstruct
    private void initHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS);
        try {
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                }
            };
            javax.net.ssl.SSLContext ssl = javax.net.ssl.SSLContext.getInstance("TLS");
            ssl.init(null, trustAll, new java.security.SecureRandom());
            builder.sslSocketFactory(ssl.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAll[0])
                   .hostnameVerifier((h, s) -> true);
        } catch (Exception ignored) {}
        this.httpClient = builder.build();
    }

    /** 是否已配置真实 API */
    public boolean isConfigured() {
        return phoneNumberId != null && !phoneNumberId.isBlank()
            && accessToken != null && !accessToken.isBlank();
    }

    /**
     * 发送 WhatsApp 文本消息
     * @param phone 手机号（国际格式，如 +8613800138000）
     * @param content 消息内容
     */
    public boolean send(String phone, String content) {
        if (phone == null || phone.isBlank()) {
            log.warn("WhatsApp发送跳过：手机号为空");
            return false;
        }

        // 格式化号码：确保国际格式
        String formattedPhone = phone.replaceAll("[\\s\\-()]", "");
        if (!formattedPhone.startsWith("+")) {
            // 中国手机号自动加 +86
            if (formattedPhone.length() == 11 && formattedPhone.startsWith("1")) {
                formattedPhone = "+86" + formattedPhone;
            } else {
                formattedPhone = "+" + formattedPhone;
            }
        }

        // 如果未配置真实 API，使用日志模拟
        if (!isConfigured()) {
            log.info("【WhatsApp模拟】未配置API凭证，消息内容: {} → {}", formattedPhone, content);
            return true;
        }

        // 真实调用 WhatsApp Cloud API
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("recipient_type", "individual");
            body.put("to", formattedPhone);
            body.put("type", "text");
            body.put("text", Map.of("preview_url", false, "body", content));

            String json = mapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(apiUrl + "/v21.0/" + phoneNumberId + "/messages")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    log.info("WhatsApp消息发送成功: phone={}", formattedPhone);
                    return true;
                } else {
                    log.error("WhatsApp发送失败: HTTP {} body={}", response.code(), respBody);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("WhatsApp发送异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 发送验证码 — 生成6位数字，存入 Redis (5分钟过期)，通过 WhatsApp 发送
     * @param phone 手机号
     * @return 验证码
     */
    public String sendVerifyCode(String phone) {
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        String content = "*WiFi管家* 📡\n您的验证码是: *" + code + "*\n有效期5分钟，请勿泄露。";

        boolean sent = send(phone, content);

        if (sent) {
            // 存入 Redis，5分钟过期
            String redisKey = "whatsapp:code:" + phone;
            redisTemplate.opsForValue().set(redisKey, code, 300, TimeUnit.SECONDS);
            log.info("WhatsApp验证码已发送并存入Redis: phone={}", phone);
        }

        return code;
    }
}
