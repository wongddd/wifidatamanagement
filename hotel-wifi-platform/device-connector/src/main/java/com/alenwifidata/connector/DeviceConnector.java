package com.alenwifidata.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 设备连接器 Agent
 *
 * 部署方式: java -jar device-connector.jar --server ws://185.239.71.210:8080/ws/device --device-id 1 --secret xxxxx
 *
 * 功能:
 *  1. 通过 WebSocket 连接到公网服务器
 *  2. 定时心跳 + 局域网设备扫描
 *  3. 接收服务器命令并在本地执行
 *  4. 代理 MikroTik REST API 调用（解决 NAT 问题）
 */
public class DeviceConnector extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(DeviceConnector.class);

    private final long deviceId;
    private final String secret;
    private final String localNetwork;      // 本地网段，如 192.168.1.0/24
    private final String mikrotikHost;       // MikroTik 路由器 IP
    private final int mikrotikPort;
    private final String mikrotikUser;
    private final String mikrotikPass;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final OkHttpClient httpClient;

    // 本地网络设备缓存
    private final Map<String, Map<String, String>> discoveredDevices = new ConcurrentHashMap<>();

    public DeviceConnector(String serverUri, long deviceId, String secret,
                           String localNetwork, String mikrotikHost, int mikrotikPort,
                           String mikrotikUser, String mikrotikPass) {
        super(URI.create(serverUri));
        this.deviceId = deviceId;
        this.secret = secret;
        this.localNetwork = localNetwork != null ? localNetwork : "192.168.1.0/24";
        this.mikrotikHost = mikrotikHost != null ? mikrotikHost : "192.168.1.1";
        this.mikrotikPort = mikrotikPort > 0 ? mikrotikPort : 8728;
        this.mikrotikUser = mikrotikUser != null ? mikrotikUser : "admin";
        this.mikrotikPass = mikrotikPass != null ? mikrotikPass : "";

        // SSL 容错（内网自签名证书常见）
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
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .sslSocketFactory(ssl.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAll[0])
                    .hostnameVerifier((h, s) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("初始化HTTP客户端失败", e);
        }
    }

    // ===== WebSocket 事件 =====

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("已连接到服务器: {}", getURI());

        // 注册
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "register");
        msg.put("deviceId", deviceId);
        msg.put("secret", secret);
        msg.put("localNetwork", localNetwork);
        msg.put("version", "1.0.0");
        send(msg);
    }

    @Override
    public void onMessage(String payload) {
        try {
            Map<String, Object> msg = mapper.readValue(payload, Map.class);
            String type = (String) msg.getOrDefault("type", "");

            switch (type) {
                case "register_ok":
                    log.info("注册成功: deviceId={}", msg.get("deviceId"));
                    startTasks();
                    break;
                case "register_reject":
                    log.error("注册被拒绝: {}", msg.get("reason"));
                    close();
                    break;
                case "command":
                    handleCommand(msg);
                    break;
                default:
                    log.debug("未知消息: {}", type);
            }
        } catch (Exception e) {
            log.error("消息处理失败", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("连接断开: code={}, reason={}, remote={}", code, reason, remote);
        scheduler.shutdownNow();
        // 5秒后重连
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (Exception ignored) {}
            log.info("尝试重连...");
            reconnect();
        }).start();
    }

    @Override
    public void onError(Exception ex) {
        log.error("连接错误: {}", ex.getMessage());
    }

    // ===== 定时任务 =====

    private void startTasks() {
        // 心跳：每30秒
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
        // 局域网扫描：每5分钟
        scheduler.scheduleAtFixedRate(this::scanLocalNetwork, 0, 5, TimeUnit.MINUTES);
        // MikroTik 状态采集：每60秒
        scheduler.scheduleAtFixedRate(this::collectMikrotikStatus, 10, 60, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try {
            Map<String, Object> hb = new HashMap<>();
            hb.put("type", "heartbeat");
            hb.put("deviceId", deviceId);
            hb.put("timestamp", System.currentTimeMillis());
            hb.put("discoveredDevices", discoveredDevices.size());
            send(hb);
        } catch (Exception e) {
            log.warn("心跳发送失败: {}", e.getMessage());
        }
    }

    /** 扫局域网设备（ARP + MikroTik DHCP） */
    private void scanLocalNetwork() {
        try {
            // 方式1: 从 MikroTik 获取 DHCP 租约（最准确）
            String json = mikrotikRestGet("/rest/ip/dhcp-server/lease");
            if (json != null) {
                List<Map<String, Object>> leases = mapper.readValue(json, List.class);
                discoveredDevices.clear();
                for (Map<String, Object> lease : leases) {
                    String mac = (String) lease.getOrDefault("mac-address", "");
                    String ip = (String) lease.getOrDefault("address", "");
                    String hostname = (String) lease.getOrDefault("host-name", "");
                    if (!mac.isEmpty()) {
                        Map<String, String> info = new HashMap<>();
                        info.put("ip", ip);
                        info.put("hostname", hostname != null ? hostname : "-");
                        info.put("mac", mac);
                        discoveredDevices.put(mac, info);
                    }
                }
                log.info("局域网扫描完成: 发现 {} 台设备", discoveredDevices.size());
            }

            // 上报新发现的设备给服务器
            for (Map.Entry<String, Map<String, String>> entry : discoveredDevices.entrySet()) {
                Map<String, Object> event = new HashMap<>();
                event.put("type", "event");
                event.put("deviceId", deviceId);
                event.put("eventType", "device_discovered");
                event.put("data", entry.getValue());
                send(event);
            }
        } catch (Exception e) {
            log.warn("局域网扫描失败: {}", e.getMessage());
        }
    }

    /** 采集 MikroTik 运行状态 */
    private void collectMikrotikStatus() {
        try {
            String resource = mikrotikRestGet("/rest/system/resource");
            if (resource != null) {
                Map<String, Object> event = new HashMap<>();
                event.put("type", "event");
                event.put("deviceId", deviceId);
                event.put("eventType", "router_status");
                event.put("data", Map.of("resource", mapper.readValue(resource, Map.class)));
                send(event);
            }
        } catch (Exception e) {
            log.debug("MikroTik状态采集失败: {}", e.getMessage());
        }
    }

    // ===== 命令执行 =====

    @SuppressWarnings("unchecked")
    private void handleCommand(Map<String, Object> msg) {
        String requestId = (String) msg.get("requestId");
        String command = (String) msg.get("command");
        Map<String, Object> params = (Map<String, Object>) msg.getOrDefault("params", Map.of());

        Map<String, Object> response = new HashMap<>();
        response.put("type", "response");
        response.put("requestId", requestId);

        try {
            String result;
            switch (command) {
                case "mikrotik_get":
                    result = mikrotikRestGet((String) params.get("path"));
                    break;
                case "mikrotik_post":
                    result = mikrotikRestPost((String) params.get("path"),
                            mapper.writeValueAsString(params.get("body")));
                    break;
                case "mikrotik_delete":
                    result = mikrotikRestDelete((String) params.get("path"));
                    break;
                case "mikrotik_hotspot_active":
                    result = mikrotikRestGet("/rest/ip/hotspot/active");
                    break;
                case "mikrotik_hotspot_user":
                    result = mikrotikRestGet("/rest/ip/hotspot/user");
                    break;
                case "ping":
                    result = pingHost((String) params.get("host"));
                    break;
                case "scan_network":
                    scanLocalNetwork();
                    result = mapper.writeValueAsString(Map.of("devices", discoveredDevices));
                    break;
                case "disconnect_user":
                    result = mikrotikRestDelete("/rest/ip/hotspot/active/" + params.get("sessionId"));
                    break;
                default:
                    result = "{\"error\":\"未知命令: " + command + "\"}";
            }
            response.put("result", result);
        } catch (Exception e) {
            response.put("result", "{\"error\":\"" + e.getMessage() + "\"}");
        }

        send(response);
    }

    // ===== MikroTik REST API 封装 =====

    private String mikrotikRestGet(String path) {
        try {
            String url = "http://" + mikrotikHost + ":" + mikrotikPort + path;
            String credential = java.util.Base64.getEncoder()
                    .encodeToString((mikrotikUser + ":" + mikrotikPass).getBytes());
            Request req = new Request.Builder().url(url)
                    .header("Authorization", "Basic " + credential).get().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                return resp.body() != null ? resp.body().string() : "[]";
            }
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String mikrotikRestPost(String path, String body) {
        try {
            String url = "http://" + mikrotikHost + ":" + mikrotikPort + path;
            String credential = java.util.Base64.getEncoder()
                    .encodeToString((mikrotikUser + ":" + mikrotikPass).getBytes());
            Request req = new Request.Builder().url(url)
                    .header("Authorization", "Basic " + credential)
                    .put(RequestBody.create(body, MediaType.parse("application/json"))).build();
            try (Response resp = httpClient.newCall(req).execute()) {
                return resp.body() != null ? resp.body().string() : "{}";
            }
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String mikrotikRestDelete(String path) {
        try {
            String url = "http://" + mikrotikHost + ":" + mikrotikPort + path;
            String credential = java.util.Base64.getEncoder()
                    .encodeToString((mikrotikUser + ":" + mikrotikPass).getBytes());
            Request req = new Request.Builder().url(url)
                    .header("Authorization", "Basic " + credential).delete().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                return resp.body() != null ? resp.body().string() : "{}";
            }
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String pingHost(String host) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"ping", "-c", "2", "-W", "3", host});
            int exit = p.waitFor();
            return "{\"reachable\":" + (exit == 0) + ",\"host\":\"" + host + "\"}";
        } catch (Exception e) {
            return "{\"reachable\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ===== 发送 JSON 消息 =====

    private void send(Map<String, Object> msg) {
        if (isOpen()) {
            try {
                super.send(mapper.writeValueAsString(msg));
            } catch (Exception e) {
                log.error("消息发送失败", e);
            }
        }
    }

    // ===== 主入口 =====

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        // 解析命令行参数
        String serverUri = System.getProperty("server", System.getenv("CONNECTOR_SERVER"));
        String deviceIdStr = System.getProperty("deviceId", System.getenv("CONNECTOR_DEVICE_ID"));
        String secret = System.getProperty("secret", System.getenv("CONNECTOR_SECRET"));
        String localNet = System.getProperty("localNetwork", System.getenv("CONNECTOR_LOCAL_NETWORK"));
        String mkHost = System.getProperty("mikrotikHost", System.getenv("CONNECTOR_MIKROTIK_HOST"));
        String mkPort = System.getProperty("mikrotikPort", System.getenv("CONNECTOR_MIKROTIK_PORT"));
        String mkUser = System.getProperty("mikrotikUser", System.getenv("CONNECTOR_MIKROTIK_USER"));
        String mkPass = System.getProperty("mikrotikPassword", System.getenv("CONNECTOR_MIKROTIK_PASSWORD"));

        if (serverUri == null || deviceIdStr == null) {
            System.out.println("用法: java -jar device-connector.jar \\");
            System.out.println("  -Dserver=ws://服务器IP:8080/ws/device \\");
            System.out.println("  -DdeviceId=1 \\");
            System.out.println("  -Dsecret=设备密钥 \\");
            System.out.println("  [-DlocalNetwork=192.168.1.0/24] \\");
            System.out.println("  [-DmikrotikHost=192.168.1.1] [-DmikrotikPort=8728]");
            System.exit(1);
        }

        long deviceId = Long.parseLong(deviceIdStr);
        int mkPortInt = mkPort != null ? Integer.parseInt(mkPort) : 8728;

        DeviceConnector connector = new DeviceConnector(
                serverUri, deviceId, secret != null ? secret : "",
                localNet, mkHost, mkPortInt, mkUser, mkPass
        );

        log.info("设备连接器启动:");
        log.info("  服务器: {}", serverUri);
        log.info("  设备ID: {}", deviceId);
        log.info("  本地网络: {}", localNet);
        log.info("  MikroTik: {}:{} (user={})", mkHost, mkPortInt, mkUser);

        connector.connect();

        // 保持运行
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭...");
            connector.close();
        }));
    }
}
