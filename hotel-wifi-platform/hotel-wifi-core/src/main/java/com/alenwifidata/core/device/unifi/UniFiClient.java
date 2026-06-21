package com.alenwifidata.core.device.unifi;

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
 * Ubiquiti UniFi 设备驱动 — 通过 UniFi Controller REST API 对接
 *
 * UniFi 在酒店市场占有率极高（仅次于 MikroTik）。
 * Controller 提供完整的 REST API，无需额外硬件。
 *
 * 认证: Cookie-based (POST /api/login → Set-Cookie)
 * API:
 *   GET  /api/s/{site}/stat/sta      → 在线客户端列表
 *   POST /api/s/{site}/cmd/stamgr    → 踢客户端 (cmd:"kick-sta", mac:"...")
 *   GET  /api/s/{site}/stat/device   → AP 设备状态
 */
@Slf4j
@Component
public class UniFiClient implements DeviceClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final Map<String, String> cookieCache = new ConcurrentHashMap<>();

    public UniFiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    @Override public String driverName() { return "UNIFI"; }
    @Override public int priority() { return 50; }

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
        } catch (Exception e) { throw new RuntimeException("UniFi HTTP Client 初始化失败", e); }
    }

    private String baseUrl(RouterDevice d) {
        String scheme = d.getApiPort() == 443 ? "https" : "http";
        return scheme + "://" + d.getHost() + ":" + d.getApiPort();
    }

    private String auth(RouterDevice d) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (d.getApiUser() + ":" + d.getApiPassword()).getBytes());
    }

    private JsonNode apiGet(RouterDevice d, String path) throws IOException {
        Request r = new Request.Builder().url(baseUrl(d) + path)
                .header("Authorization", auth(d)).get().build();
        try (Response resp = httpClient.newCall(r).execute()) {
            return resp.isSuccessful() && resp.body() != null
                    ? objectMapper.readTree(resp.body().string()) : null;
        }
    }

    // ============ DeviceClient ============

    @Override public boolean testConnection(RouterDevice device) {
        try {
            Request r = new Request.Builder().url(baseUrl(device) + "/api/login")
                    .post(RequestBody.create("{\"username\":\"" + device.getApiUser()
                            + "\",\"password\":\"" + device.getApiPassword() + "\"}",
                            MediaType.parse("application/json"))).build();
            try (Response resp = httpClient.newCall(r).execute()) {
                return resp.isSuccessful();
            }
        } catch (Exception e) { return false; }
    }

    @Override
    public List<ActiveSession> getActiveSessions(RouterDevice device) {
        List<ActiveSession> sessions = new ArrayList<>();
        try {
            // UniFi 默认 site = "default"
            JsonNode data = apiGet(device, "/api/s/default/stat/sta");
            if (data != null && data.has("data")) {
                for (JsonNode sta : data.get("data")) {
                    sessions.add(ActiveSession.builder()
                            .sessionId(sta.has("_id") ? sta.get("_id").asText() : "")
                            .username(sta.has("hostname") ? sta.get("hostname").asText() : "")
                            .macAddress(sta.has("mac") ? sta.get("mac").asText() : "")
                            .ipAddress(sta.has("ip") ? sta.get("ip").asText() : "")
                            .bytesIn(sta.has("rx_bytes") ? sta.get("rx_bytes").asLong() : 0)
                            .bytesOut(sta.has("tx_bytes") ? sta.get("tx_bytes").asLong() : 0)
                            .uptimeSeconds(sta.has("uptime") ? sta.get("uptime").asLong() : 0)
                            .loginAt(LocalDateTime.now())
                            .driverName(driverName()).build());
                }
            }
        } catch (Exception e) {
            log.error("UniFi 采集客户端失败: {}", e.getMessage());
        }
        return sessions;
    }

    @Override
    public boolean kickUser(RouterDevice device, String sessionId) {
        try {
            String json = "{\"cmd\":\"kick-sta\",\"mac\":\"" + sessionId + "\"}";
            Request r = new Request.Builder().url(baseUrl(device) + "/api/s/default/cmd/stamgr")
                    .header("Authorization", auth(device))
                    .post(RequestBody.create(json, MediaType.parse("application/json"))).build();
            try (Response resp = httpClient.newCall(r).execute()) {
                return resp.isSuccessful();
            }
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean setBandwidthLimit(RouterDevice device, String target,
                                     long uploadBps, long downloadBps) {
        try {
            String json = "{\"cmd\":\"set-sta-limit\",\"mac\":\"" + target
                    + "\",\"up\":" + (uploadBps / 1000) + ",\"down\":" + (downloadBps / 1000) + "}";
            Request r = new Request.Builder().url(baseUrl(device) + "/api/s/default/cmd/stamgr")
                    .header("Authorization", auth(device))
                    .post(RequestBody.create(json, MediaType.parse("application/json"))).build();
            try (Response resp = httpClient.newCall(r).execute()) {
                return resp.isSuccessful();
            }
        } catch (Exception e) { return false; }
    }

    @Override
    public DeviceMetrics getMetrics(RouterDevice device) {
        try {
            JsonNode data = apiGet(device, "/api/s/default/stat/device");
            if (data != null && data.has("data") && data.get("data").size() > 0) {
                JsonNode ap = data.get("data").get(0);
                return DeviceMetrics.builder().driverName(driverName())
                        .cpuLoad(ap.has("system-stats") && ap.get("system-stats").has("cpu")
                                ? ap.get("system-stats").get("cpu").asText() + "%" : null)
                        .memoryUsed(ap.has("system-stats") && ap.get("system-stats").has("mem")
                                ? ap.get("system-stats").get("mem").asLong() : null)
                        .uptimeSeconds(ap.has("uptime") ? ap.get("uptime").asLong() : 0)
                        .clientCount(ap.has("num_sta") ? ap.get("num_sta").asInt() : 0)
                        .build();
            }
        } catch (Exception e) {
            log.error("UniFi 采集性能: {}", e.getMessage());
        }
        return DeviceMetrics.builder().driverName(driverName()).build();
    }
}
