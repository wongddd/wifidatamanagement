package com.alenwifidata.core.device.openwrt;

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
 * OpenWRT 设备驱动 — 通过 ubus JSON-RPC / LuCI HTTP 对接
 *
 * OpenWRT 是开源路由器固件，在廉价方案和自建网关中广泛应用。
 * 需要安装: rpcd + uhttpd-mod-ubus 插件
 *
 * API:
 *   ubus call (JSON-RPC over HTTP):
 *     /ubus call session login              → 获取 session
 *     /ubus call iwinfo assoclist           → 在线客户端
 *     /ubus call hostapd.wlan0 del_client   → 踢客户端
 *     /ubus call system board               → 设备信息
 */
@Slf4j
@Component
public class OpenWRTClient implements DeviceClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final Map<String, String> sessionCache = new ConcurrentHashMap<>();

    public OpenWRTClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    @Override public String driverName() { return "OPENWRT"; }
    @Override public int priority() { return 90; }

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
        } catch (Exception e) { throw new RuntimeException("OpenWRT HTTP Client 初始化失败", e); }
    }

    private String baseUrl(RouterDevice d) {
        String scheme = d.getApiPort() == 443 ? "https" : "http";
        return scheme + "://" + d.getHost() + ":" + d.getApiPort();
    }

    private String ubus(RouterDevice d, String params) throws IOException {
        String session = getSession(d);
        if (session == null) return null;
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"call\",\"params\":" + params + "}";
        Request r = new Request.Builder().url(baseUrl(d) + "/ubus")
                .header("Cookie", "ubus-rpc-session=" + session)
                .post(RequestBody.create(json, MediaType.parse("application/json"))).build();
        try (Response resp = httpClient.newCall(r).execute()) {
            return resp.isSuccessful() && resp.body() != null ? resp.body().string() : null;
        }
    }

    private String getSession(RouterDevice d) {
        String key = d.getHost();
        String cached = sessionCache.get(key);
        if (cached != null) return cached;

        try {
            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"call\",\"params\":"
                    + "[\"00000000000000000000000000000000\",\"session\",\"login\","
                    + "{\"username\":\"" + d.getApiUser() + "\",\"password\":\"" + d.getApiPassword() + "\"}]}";
            Request r = new Request.Builder().url(baseUrl(d) + "/ubus")
                    .post(RequestBody.create(body, MediaType.parse("application/json"))).build();
            try (Response resp = httpClient.newCall(r).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    JsonNode json = objectMapper.readTree(resp.body().string());
                    if (json.has("result") && json.get("result").size() > 1) {
                        String session = json.get("result").get(1).get("ubus_rpc_session").asText();
                        sessionCache.put(key, session);
                        return session;
                    }
                }
            }
        } catch (Exception e) { log.error("OpenWRT ubus 登录失败: {}", e.getMessage()); }
        return null;
    }

    // ============ DeviceClient ============

    @Override public boolean testConnection(RouterDevice device) {
        try {
            String session = getSession(device);
            return session != null && !session.isEmpty();
        } catch (Exception e) { return false; }
    }

    @Override
    public List<ActiveSession> getActiveSessions(RouterDevice device) {
        List<ActiveSession> sessions = new ArrayList<>();
        try {
            String resp = ubus(device, "[\"iwinfo\",\"assoclist\",{\"device\":\"wlan0\"}]");
            if (resp != null) {
                JsonNode data = objectMapper.readTree(resp);
                if (data.has("result") && data.get("result").size() > 1) {
                    JsonNode clients = data.get("result").get(1).get("results");
                    if (clients != null) {
                        for (JsonNode c : clients) {
                            sessions.add(ActiveSession.builder()
                                    .sessionId(c.has("mac") ? c.get("mac").asText() : "")
                                    .macAddress(c.has("mac") ? c.get("mac").asText() : "")
                                    .bytesIn(c.has("rx_bytes") ? c.get("rx_bytes").asLong() : 0)
                                    .bytesOut(c.has("tx_bytes") ? c.get("tx_bytes").asLong() : 0)
                                    .uptimeSeconds(c.has("inactive") ? c.get("inactive").asLong() / 1000 : 0)
                                    .loginAt(LocalDateTime.now())
                                    .driverName(driverName()).build());
                        }
                    }
                }
            }
        } catch (Exception e) { log.error("OpenWRT 采集客户端: {}", e.getMessage()); }
        return sessions;
    }

    @Override
    public boolean kickUser(RouterDevice device, String sessionId) {
        try {
            String resp = ubus(device, "[\"hostapd.wlan0\",\"del_client\","
                    + "{\"addr\":\"" + sessionId + "\",\"reason\":1,\"deauth\":true}]");
            return resp != null && resp.contains("\"result\"");
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean setBandwidthLimit(RouterDevice device, String target,
                                     long uploadBps, long downloadBps) {
        // OpenWRT 通过 tc qdisc/SQM 实现，不在此 API 层面
        log.warn("OpenWRT 限速需通过 tc/SQM 配置");
        return false;
    }

    @Override
    public DeviceMetrics getMetrics(RouterDevice device) {
        try {
            String resp = ubus(device, "[\"system\",\"board\",{}]");
            if (resp != null) {
                JsonNode data = objectMapper.readTree(resp);
                if (data.has("result") && data.get("result").size() > 1
                        && data.get("result").get(1).has("uptime")) {
                    return DeviceMetrics.builder().driverName(driverName())
                            .uptimeSeconds(data.get("result").get(1).get("uptime").asLong())
                            .build();
                }
            }
        } catch (Exception e) { log.error("OpenWRT 采集性能: {}", e.getMessage()); }
        return DeviceMetrics.builder().driverName(driverName()).build();
    }
}
