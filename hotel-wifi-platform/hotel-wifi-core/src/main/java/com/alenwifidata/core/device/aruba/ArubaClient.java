package com.alenwifidata.core.device.aruba;

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
import java.util.concurrent.TimeUnit;

/**
 * Aruba Instant 设备驱动 — 本地 REST API (端口 4343)
 *
 * Aruba Instant AP 自带本地 REST API，无需外部 Controller。
 * 开箱即用，适合中小型酒店。
 *
 * API:
 *   GET /v1/monitor/aps               → AP 列表
 *   GET /v1/monitor/clients/wireless  → 在线无线客户端
 *   POST /v1/command/disconnect       → 踢客户端 ({"mac":"xx:xx:xx:xx:xx:xx"})
 */
@Slf4j
@Component
public class ArubaClient implements DeviceClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public ArubaClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    @Override public String driverName() { return "ARUBA"; }
    @Override public int priority() { return 70; }

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
        } catch (Exception e) { throw new RuntimeException("Aruba HTTP Client 初始化失败", e); }
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
            JsonNode data = apiGet(device, "/v1/monitor/aps");
            return data != null && data.has("aps");
        } catch (Exception e) { return false; }
    }

    @Override
    public List<ActiveSession> getActiveSessions(RouterDevice device) {
        List<ActiveSession> sessions = new ArrayList<>();
        try {
            JsonNode data = apiGet(device, "/v1/monitor/clients/wireless");
            if (data != null && data.has("clients")) {
                for (JsonNode c : data.get("clients")) {
                    sessions.add(ActiveSession.builder()
                            .sessionId(c.has("mac") ? c.get("mac").asText() : "")
                            .username(c.has("username") ? c.get("username").asText() : "")
                            .macAddress(c.has("mac") ? c.get("mac").asText() : "")
                            .ipAddress(c.has("ip_address") ? c.get("ip_address").asText() : "")
                            .bytesIn(c.has("rx_bytes") ? c.get("rx_bytes").asLong() : 0)
                            .bytesOut(c.has("tx_bytes") ? c.get("tx_bytes").asLong() : 0)
                            .uptimeSeconds(c.has("uptime") ? c.get("uptime").asLong() : 0)
                            .loginAt(LocalDateTime.now())
                            .driverName(driverName()).build());
                }
            }
        } catch (Exception e) { log.error("Aruba 采集客户端: {}", e.getMessage()); }
        return sessions;
    }

    @Override
    public boolean kickUser(RouterDevice device, String sessionId) {
        try {
            String json = "{\"mac\":\"" + sessionId + "\"}";
            Request r = new Request.Builder().url(baseUrl(device) + "/v1/command/disconnect")
                    .header("Authorization", auth(device))
                    .post(RequestBody.create(json, MediaType.parse("application/json"))).build();
            try (Response resp = httpClient.newCall(r).execute()) { return resp.isSuccessful(); }
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean setBandwidthLimit(RouterDevice device, String target,
                                     long uploadBps, long downloadBps) {
        // Aruba Instant 通过 WLAN/SSID 或 User Role 限速，非 per-client API
        log.warn("Aruba 限速需通过 WLAN 配置实现");
        return false;
    }

    @Override
    public DeviceMetrics getMetrics(RouterDevice device) {
        try {
            JsonNode data = apiGet(device, "/v1/monitor/aps");
            if (data != null && data.has("aps") && data.get("aps").size() > 0) {
                JsonNode ap = data.get("aps").get(0);
                return DeviceMetrics.builder().driverName(driverName())
                        .cpuLoad(ap.has("cpu_utilization") ? ap.get("cpu_utilization").asText() + "%" : null)
                        .memoryUsed(ap.has("mem_used") ? ap.get("mem_used").asLong() : null)
                        .memoryTotal(ap.has("mem_total") ? ap.get("mem_total").asLong() : null)
                        .uptimeSeconds(ap.has("uptime") ? ap.get("uptime").asLong() : 0)
                        .clientCount(ap.has("associated_clients") ? ap.get("associated_clients").asInt() : 0)
                        .build();
            }
        } catch (Exception e) { log.error("Aruba 采集性能: {}", e.getMessage()); }
        return DeviceMetrics.builder().driverName(driverName()).build();
    }
}
