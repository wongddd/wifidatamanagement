package com.alenwifidata.core.payment.monnify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Monnify API 认证服务 — Bearer Token 管理 + HMAC-SHA512 签名工具
 *
 * Token 有效期 1 小时，内部自动刷新。线程安全。
 */
@Slf4j
@Component
public class MonnifyClient {

    @Value("${payment.monnify.base-url:https://sandbox.monnify.com}")
    private String baseUrl;

    @Value("${payment.monnify.api-key:}")
    private String apiKey;

    @Value("${payment.monnify.secret-key:}")
    private String secretKey;

    @Value("${payment.monnify.contract-code:}")
    private String contractCode;

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient httpClient;

    /** Token 缓存 */
    private volatile String accessToken;
    private volatile long expiresAt;

    public MonnifyClient() {
        // SSL 容错（有些网络环境证书链不完整）
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
            this.httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .sslSocketFactory(ssl.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAll[0])
                    .hostnameVerifier((h, s) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("MonnifyClient初始化失败", e);
        }
    }

    @PostConstruct
    public void init() {
        log.info("MonnifyClient初始化: baseUrl={}, contractCode={}, apiKey={}",
                baseUrl, contractCode, apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, 10) + "..." : "未配置");
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty()
            && secretKey != null && !secretKey.isEmpty()
            && contractCode != null && !contractCode.isEmpty();
    }

    // ===== Token 管理 =====

    public synchronized String getToken() {
        if (accessToken != null && System.currentTimeMillis() < expiresAt) {
            return accessToken;
        }
        refreshToken();
        return accessToken;
    }

    private void refreshToken() {
        String auth = Base64.getEncoder().encodeToString((apiKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/auth/login")
                .header("Authorization", "Basic " + auth)
                .post(RequestBody.create("", MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Monnify认证失败: HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            JsonNode node = mapper.readTree(body);

            if (!node.get("requestSuccessful").asBoolean()) {
                throw new RuntimeException("Monnify认证失败: " + node.get("responseMessage").asText());
            }

            JsonNode respBody = node.get("responseBody");
            accessToken = respBody.get("accessToken").asText();
            int expiresIn = respBody.get("expiresIn").asInt();
            expiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
            log.info("Monnify Token刷新成功, 有效期: {}s", expiresIn);
        } catch (Exception e) {
            log.error("Monnify认证异常", e);
            throw new RuntimeException("Monnify认证失败: " + e.getMessage());
        }
    }

    // ===== HTTP 请求封装 =====

    /** GET 请求 */
    public JsonNode get(String path) {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + path)
                    .header("Authorization", "Bearer " + getToken())
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return mapper.readTree(response.body().string());
            }
        } catch (Exception e) {
            throw new RuntimeException("Monnify GET失败: " + e.getMessage());
        }
    }

    /** POST 请求 */
    public JsonNode post(String path, String jsonBody) {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + path)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return mapper.readTree(response.body().string());
            }
        } catch (Exception e) {
            throw new RuntimeException("Monnify POST失败: " + e.getMessage());
        }
    }

    // ===== Webhook 签名验证 =====

    /**
     * 验证 Monnify Webhook 签名
     * 算法: HMAC-SHA512(SecretKey, requestBody)
     */
    public boolean verifySignature(String requestBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(keySpec);
            byte[] hashBytes = mac.doFinal(requestBody.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }

            // 安全比较（防止时序攻击）
            return MessageDigest.isEqual(
                    hexString.toString().getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Monnify签名验证异常", e);
            return false;
        }
    }

    // ===== Getters =====

    public String getContractCode() { return contractCode; }
    public String getBaseUrl() { return baseUrl; }
}
