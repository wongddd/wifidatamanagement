package com.alenwifidata.core.device.mikrotik;

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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MikroTik RouterOS REST API 客户端
 * 参考: https://help.mikrotik.com/docs/display/ROS/REST+API
 */
@Slf4j
@Component
public class MikroTikClient {

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

    private OkHttpClient buildHttpClient() {
        try {
            // 忽略 SSL 证书验证（内网环境常见自签名证书）
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
                    .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MikroTik HTTP client", e);
        }
    }

    /**
     * 获取认证头
     */
    private String getAuthHeader(RouterDevice device) {
        String credentials = device.getApiUser() + ":" + device.getApiPassword();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * 构建请求 URL
     */
    private String buildUrl(RouterDevice device, String path) {
        String scheme = device.getApiPort() == 443 ? "https" : "http";
        return scheme + "://" + device.getHost() + ":" + device.getApiPort() + path;
    }

    /**
     * 通用 GET 请求
     */
    public JsonNode get(RouterDevice device, String path) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(device, path))
                .header("Authorization", getAuthHeader(device))
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("MikroTik GET failed: {} {} -> {}", device.getHost(), path, response.code());
                return null;
            }
            String body = response.body() != null ? response.body().string() : "[]";
            return objectMapper.readTree(body);
        }
    }

    /**
     * 获取活跃 Hotspot 用户
     */
    public JsonNode getActiveHotspotUsers(RouterDevice device) throws IOException {
        return get(device, "/rest/ip/hotspot/active");
    }

    /**
     * 获取特定用户的活跃会话
     */
    public JsonNode getActiveUser(RouterDevice device, String username) throws IOException {
        return get(device, "/rest/ip/hotspot/active?user=" + username);
    }

    /**
     * 获取 Hotspot 用户列表
     */
    public JsonNode getHotspotUsers(RouterDevice device) throws IOException {
        return get(device, "/rest/ip/hotspot/user");
    }

    /**
     * 踢用户下线
     */
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

    /**
     * 获取系统资源信息
     */
    public JsonNode getSystemResource(RouterDevice device) throws IOException {
        return get(device, "/rest/system/resource");
    }

    /**
     * 获取接口流量
     */
    public JsonNode getInterfaceTraffic(RouterDevice device, String interfaceName) throws IOException {
        return get(device, "/rest/interface/" + interfaceName);
    }

    /**
     * 测试连接
     */
    public boolean testConnection(RouterDevice device) {
        try {
            JsonNode result = getSystemResource(device);
            return result != null && result.has("uptime");
        } catch (Exception e) {
            log.warn("MikroTik connection test failed for {}: {}", device.getHost(), e.getMessage());
            return false;
        }
    }

    /**
     * 更新 Simple Queue 限速
     */
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

    /**
     * 删除 Simple Queue
     */
    public void removeSimpleQueue(RouterDevice device, String queueId) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(device, "/rest/queue/simple/" + queueId))
                .header("Authorization", getAuthHeader(device))
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            log.debug("Removed queue {} from {}", queueId, device.getHost());
        }
    }
}
