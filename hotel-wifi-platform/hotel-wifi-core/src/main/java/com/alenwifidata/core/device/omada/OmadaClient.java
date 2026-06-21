package com.alenwifidata.core.device.omada;

import com.alenwifidata.core.device.client.DeviceClient;
import com.alenwifidata.core.device.model.ActiveSession;
import com.alenwifidata.core.device.model.DeviceMetrics;
import com.alenwifidata.core.device.model.RouterDevice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * TP-Link Omada 设备驱动 — 通过 Omada Controller Open API 对接
 *
 * Omada 是 TP-Link 的企业级 SDN 方案，在性价比场景中广泛应用。
 * Controller 提供 Bearer Token 认证的 REST API。
 *
 * API:
 *   POST /api/v2/login               → token (Bearer)
 *   GET  /api/v2/sites/{id}/clients  → 在线客户端
 *   POST /api/v2/sites/{id}/clients/{mac}/disconnect → 踢客户端
 */
@Slf4j
@Component
public class OmadaClient implements DeviceClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();

    private static class TokenCache {
        String token; long expiresAt;
        boolean valid() { return token != null && System.currentTimeMillis() < expiresAt; }
    }

    public OmadaClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    @Override public String driverName() { return "OMADA"; }
    @Override public int priority() { return 60; }

    private OkHttpClient buildHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trustAll, new java.security.SecureRandom());
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                    .sslSocketFactory(ssl.getSocketFactory(), (X509TrustManager) trustAll[0])
                    .hostnameVerifier((h, s) -> true).build();
        } catch (Exception e) { throw new RuntimeException("Omada HTTP Client 初始化失败", e); }
    }

    private String baseUrl(RouterDevice d) {
        String scheme = d.getApiPort() == 443 ? "https" : "http";
        return scheme + "://" + d.getHost() + ":" + d.getApiPort();
    }

    private String getToken(RouterDevice d) throws IOException {
        String key = d.getHost() + ":" + d.getApiPort();
        TokenCache cache = tokenCache.get(key);
        if (cache != null && cache.valid()) return cache.token;

        String body = "{\"username\":\"" + d.getApiUser() + "\",\"password\":\"" + d.getApiPassword() + "\"}";
        Request r = new Request.Builder().url(baseUrl(d) + "/api/v2/login")
                .post(RequestBody.create(body, MediaType.parse("application/json"))).build();
        try (Response resp = httpClient.newCall(r).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            JsonNode json = objectMapper.readTree(resp.body().string());
            String token = json.has("result") && json.get("result").has("token")
                    ? json.get("result").get("token").asText() : null;
            if (token != null) {
                TokenCache c = new TokenCache();
                c.token = token;
                c.expiresAt = System.currentTimeMillis() + 3600_000; // 1h
                tokenCache.put(key, c);
            }
            return token;
        }
    }

    private JsonNode apiGet(RouterDevice d, String path) throws IOException {
        String token = getToken(d);
        if (token == null) return null;
        Request r = new Request.Builder().url(baseUrl(d) + path)
                .header("Authorization", "Bearer " + token).get().build();
        try (Response resp = httpClient.newCall(r).execute()) {
            return resp.isSuccessful() && resp.body() != null
                    ? objectMapper.readTree(resp.body().string()) : null;
        }
    }

    // ============ DeviceClient ============

    @Override public boolean testConnection(RouterDevice device) {
        try { return getToken(device) != null; } catch (Exception e) { return false; }
    }

    @Override
    public List<ActiveSession> getActiveSessions(RouterDevice device) {
        List<ActiveSession> sessions = new ArrayList<>();
        try {
            JsonNode data = apiGet(device, "/api/v2/sites/default/clients");
            if (data != null && data.has("result")) {
                for (JsonNode c : data.get("result")) {
                    sessions.add(ActiveSession.builder()
                            .sessionId(c.has("mac") ? c.get("mac").asText() : "")
                            .username(c.has("name") ? c.get("name").asText() : "")
                            .macAddress(c.has("mac") ? c.get("mac").asText() : "")
                            .ipAddress(c.has("ip") ? c.get("ip").asText() : "")
                            .bytesIn(c.has("downPkt") ? c.get("downPkt").asLong() : 0)
                            .bytesOut(c.has("upPkt") ? c.get("upPkt").asLong() : 0)
                            .loginAt(LocalDateTime.now())
                            .driverName(driverName()).build());
                }
            }
        } catch (Exception e) { log.error("Omada 采集客户端: {}", e.getMessage()); }
        return sessions;
    }

    @Override
    public boolean kickUser(RouterDevice device, String sessionId) {
        try {
            String token = getToken(device);
            Request r = new Request.Builder()
                    .url(baseUrl(device) + "/api/v2/sites/default/clients/" + sessionId + "/disconnect")
                    .header("Authorization", "Bearer " + token)
                    .post(RequestBody.create("{}", MediaType.parse("application/json"))).build();
            try (Response resp = httpClient.newCall(r).execute()) { return resp.isSuccessful(); }
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean setBandwidthLimit(RouterDevice device, String target,
                                     long uploadBps, long downloadBps) {
        try {
            String token = getToken(device);
            String json = "{\"rateLimit\":{\"downRate\":" + (downloadBps / 1000)
                    + ",\"upRate\":" + (uploadBps / 1000) + "}}";
            Request r = new Request.Builder()
                    .url(baseUrl(device) + "/api/v2/sites/default/clients/" + target)
                    .header("Authorization", "Bearer " + token)
                    .put(RequestBody.create(json, MediaType.parse("application/json"))).build();
            try (Response resp = httpClient.newCall(r).execute()) { return resp.isSuccessful(); }
        } catch (Exception e) { return false; }
    }

    @Override
    public DeviceMetrics getMetrics(RouterDevice device) {
        return DeviceMetrics.builder().driverName(driverName()).clientCount(0).build();
    }
}
