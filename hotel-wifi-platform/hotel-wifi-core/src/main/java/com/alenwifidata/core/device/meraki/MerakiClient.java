package com.alenwifidata.core.device.meraki;

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
 * Cisco Meraki 设备驱动 — Dashboard Cloud API
 *
 * Meraki 是云端管理的网络方案，设备必须能访问 api.meraki.com。
 *
 * API (需 Meraki 许可证):
 *   api.meraki.com/api/v1/networks/{networkId}/clients
 *   认证: X-Cisco-Meraki-API-Key header
 *
 * 注意: Meraki 需要付费许可证，测试成本高。本驱动实现 API 对接框架。
 */
@Slf4j
@Component
public class MerakiClient implements DeviceClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    private static final String MERAKI_API = "https://api.meraki.com/api/v1";

    public MerakiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    @Override public String driverName() { return "MERAKI"; }
    @Override public int priority() { return 100; }

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
                    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                    .sslSocketFactory(ssl.getSocketFactory(), (X509TrustManager) trustAll[0])
                    .hostnameVerifier((h, s) -> true).build();
        } catch (Exception e) { throw new RuntimeException("Meraki HTTP Client 初始化失败", e); }
    }

    private JsonNode apiGet(RouterDevice d, String path) throws IOException {
        Request r = new Request.Builder().url(MERAKI_API + path)
                .header("X-Cisco-Meraki-API-Key", d.getApiPassword())
                .get().build();
        try (Response resp = httpClient.newCall(r).execute()) {
            return resp.isSuccessful() && resp.body() != null
                    ? objectMapper.readTree(resp.body().string()) : null;
        }
    }

    // ============ DeviceClient ============

    @Override
    public boolean testConnection(RouterDevice device) {
        try {
            // Meraki: 查询本 API Key 的组织信息
            JsonNode data = apiGet(device, "/organizations");
            return data != null && data.isArray() && data.size() > 0;
        } catch (Exception e) {
            log.error("Meraki API 不可达 (可能需许可证): {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<ActiveSession> getActiveSessions(RouterDevice device) {
        List<ActiveSession> sessions = new ArrayList<>();
        try {
            // host 字段存储 networkId, apiUser 存 organizationId
            String networkId = device.getHost();
            JsonNode data = apiGet(device, "/networks/" + networkId + "/clients");
            if (data != null) {
                for (JsonNode c : data) {
                    sessions.add(ActiveSession.builder()
                            .sessionId(c.has("id") ? c.get("id").asText() : "")
                            .username(c.has("description") ? c.get("description").asText() : "")
                            .macAddress(c.has("mac") ? c.get("mac").asText() : "")
                            .ipAddress(c.has("ip") ? c.get("ip").asText() : "")
                            .bytesIn(c.has("usage") && c.get("usage").has("recv")
                                    ? c.get("usage").get("recv").asLong() : 0)
                            .bytesOut(c.has("usage") && c.get("usage").has("sent")
                                    ? c.get("usage").get("sent").asLong() : 0)
                            .loginAt(LocalDateTime.now())
                            .driverName(driverName()).build());
                }
            }
        } catch (Exception e) { log.error("Meraki 采集客户端: {}", e.getMessage()); }
        return sessions;
    }

    @Override
    public boolean kickUser(RouterDevice device, String sessionId) {
        // Meraki 没有直接踢客户端 API，通过修改策略实现
        log.warn("Meraki 踢客户端需通过 Dashboard UI 操作或修改 group policy");
        return false;
    }

    @Override
    public boolean setBandwidthLimit(RouterDevice device, String target,
                                     long uploadBps, long downloadBps) {
        log.warn("Meraki 限速需通过 Traffic Shaping 规则或 Group Policy 实现");
        return false;
    }

    @Override
    public DeviceMetrics getMetrics(RouterDevice device) {
        return DeviceMetrics.builder().driverName(driverName()).clientCount(0).build();
    }
}
