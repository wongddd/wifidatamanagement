package com.alenwifidata.core.device.mikrotik;

import com.alenwifidata.core.device.client.DeviceClient;
import com.alenwifidata.core.device.model.ActiveSession;
import com.alenwifidata.core.device.model.DeviceMetrics;
import com.alenwifidata.core.device.model.RouterDevice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * MikroTik RouterOS REST API 驱动 —— 实现 DeviceClient 接口
 * 参考: https://help.mikrotik.com/docs/display/ROS/REST+API
 */
@Slf4j
@Component
public class MikroTikClient implements DeviceClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Value("${mikrotik.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${mikrotik.read-timeout:30000}")
    private int readTimeout;

    public MikroTikClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    // ==================== DeviceClient 接口实现 ====================

    @Override
    public String driverName() {
        return "MIKROTIK";
    }

    @Override
    public int priority() {
        return 10; // 最高优先级，作为默认驱动
    }

    @Override
    public boolean testConnection(RouterDevice device) {
        try {
            JsonNode result = getSystemResource(device);
            return result != null && result.has("uptime");
        } catch (Exception e) {
            log.warn("MikroTik 连接测试失败 {}: {}", device.getHost(), e.getMessage());
            return false;
        }
    }

    @Override
    public List<ActiveSession> getActiveSessions(RouterDevice device) {
        try {
            JsonNode activeUsers = getActiveHotspotUsers(device);
            if (activeUsers == null || !activeUsers.isArray()) {
                return Collections.emptyList();
            }

            List<ActiveSession> sessions = new ArrayList<>();
            for (JsonNode user : activeUsers) {
                ActiveSession session = ActiveSession.builder()
                        .sessionId(user.has(".id") ? user.get(".id").asText() : "")
                        .username(user.has("user") ? user.get("user").asText() : "")
                        .macAddress(user.has("mac-address") ? user.get("mac-address").asText() : "")
                        .ipAddress(user.has("address") ? user.get("address").asText() : "")
                        .bytesIn(parseLong(user, "bytes-in"))
                        .bytesOut(parseLong(user, "bytes-out"))
                        .uptimeSeconds(parseDuration(user, "uptime"))
                        .loginAt(LocalDateTime.now()) // MikroTik 不直接提供 loginAt
                        .driverName(driverName())
                        .build();
                sessions.add(session);
            }
            return sessions;
        } catch (Exception e) {
            log.error("MikroTik 采集活跃用户失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean kickUser(RouterDevice device, String sessionId) {
        try {
            removeActiveUser(device, sessionId);
            return true;
        } catch (Exception e) {
            log.error("MikroTik 踢用户下线失败: sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean setBandwidthLimit(RouterDevice device, String target,
                                     long uploadBps, long downloadBps) {
        try {
            setSimpleQueue(device, target, "hotel-limit-" + target,
                    uploadBps, downloadBps);
            return true;
        } catch (Exception e) {
            log.error("MikroTik 限速设置失败: target={}, error={}", target, e.getMessage());
            return false;
        }
    }

    @Override
    public DeviceMetrics getMetrics(RouterDevice device) {
        try {
            JsonNode resource = getSystemResource(device);
            if (resource == null) {
                return DeviceMetrics.builder().driverName(driverName()).build();
            }

            return DeviceMetrics.builder()
                    .driverName(driverName())
                    .cpuLoad(resource.has("cpu-load") ? resource.get("cpu-load").asText() + "%" : null)
                    .memoryUsed(resource.has("free-memory") && resource.has("total-memory")
                            ? parseLong(resource, "total-memory") - parseLong(resource, "free-memory")
                            : null)
                    .memoryTotal(resource.has("total-memory") ? parseLong(resource, "total-memory") : null)
                    .uptimeSeconds(parseDuration(resource, "uptime"))
                    .build();
        } catch (Exception e) {
            log.error("MikroTik 采集性能指标失败: {}", e.getMessage());
            return DeviceMetrics.builder().driverName(driverName()).build();
        }
    }

    // ==================== 原始 REST API 方法（保留向后兼容） ====================

    private OkHttpClient buildHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            return new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("MikroTik HTTP 客户端初始化失败", e);
        }
    }

    private String getAuthHeader(RouterDevice device) {
        String credentials = device.getApiUser() + ":" + device.getApiPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    private String buildUrl(RouterDevice device, String path) {
        String scheme = device.getApiPort() == 443 ? "https" : "http";
        return scheme + "://" + device.getHost() + ":" + device.getApiPort() + path;
    }

    public JsonNode get(RouterDevice device, String path) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(device, path))
                .header("Authorization", getAuthHeader(device))
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("MikroTik GET 失败: {} {} -> {}", device.getHost(), path, response.code());
                return null;
            }
            String body = response.body() != null ? response.body().string() : "[]";
            return objectMapper.readTree(body);
        }
    }

    public JsonNode getActiveHotspotUsers(RouterDevice device) throws IOException {
        return get(device, "/rest/ip/hotspot/active");
    }

    public JsonNode getHotspotUsers(RouterDevice device) throws IOException {
        return get(device, "/rest/ip/hotspot/user");
    }

    public JsonNode getSystemResource(RouterDevice device) throws IOException {
        return get(device, "/rest/system/resource");
    }

    public JsonNode getInterfaceTraffic(RouterDevice device, String interfaceName) throws IOException {
        return get(device, "/rest/interface/" + interfaceName);
    }

    public JsonNode removeActiveUser(RouterDevice device, String id) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(device, "/rest/ip/hotspot/active/" + id))
                .header("Authorization", getAuthHeader(device))
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            return objectMapper.readTree(body);
        }
    }

    public JsonNode setSimpleQueue(RouterDevice device, String target, String queueName,
                                    long maxUploadBps, long maxDownloadBps) throws IOException {
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("name", queueName);
        queueData.put("target", target);
        queueData.put("max-limit", (maxUploadBps / 1000000) + "M/" + (maxDownloadBps / 1000000) + "M");

        String json = objectMapper.writeValueAsString(queueData);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(buildUrl(device, "/rest/queue/simple"))
                .header("Authorization", getAuthHeader(device))
                .put(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "{}";
            return objectMapper.readTree(respBody);
        }
    }

    public void removeSimpleQueue(RouterDevice device, String queueId) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(device, "/rest/queue/simple/" + queueId))
                .header("Authorization", getAuthHeader(device))
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            log.debug("已删除 Simple Queue: {} @ {}", queueId, device.getHost());
        }
    }

    // ==================== 工具方法 ====================

    private long parseLong(JsonNode node, String field) {
        if (node == null || !node.has(field)) return 0;
        try {
            JsonNode fieldNode = node.get(field);
            if (fieldNode.isNumber()) return fieldNode.asLong();
            return Long.parseLong(fieldNode.asText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseDuration(JsonNode node, String field) {
        if (node == null || !node.has(field)) return 0;
        String text = node.get(field).asText();
        try {
            // MikroTik 格式: "1w2d3h4m5s" 或纯秒数
            if (text.matches("\\d+")) {
                return Long.parseLong(text);
            }
            long seconds = 0;
            int num = 0;
            for (char c : text.toCharArray()) {
                if (c >= '0' && c <= '9') {
                    num = num * 10 + (c - '0');
                } else {
                    switch (c) {
                        case 'w': seconds += num * 604800L; break;
                        case 'd': seconds += num * 86400L; break;
                        case 'h': seconds += num * 3600L; break;
                        case 'm': seconds += num * 60L; break;
                        case 's': seconds += num; break;
                    }
                    num = 0;
                }
            }
            return seconds;
        } catch (Exception e) {
            return 0;
        }
    }
}
