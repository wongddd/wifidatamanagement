package com.alenwifidata.core.device.relay;

import com.alenwifidata.common.dto.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备中继管理 API
 */
@RestController
@RequestMapping("/api/v1/devices/relay")
@RequiredArgsConstructor
public class DeviceRelayController {

    private final DeviceRelayHandler relayHandler;

    /** 检查设备 Agent 在线状态 */
    @GetMapping("/{deviceId}/status")
    public ApiResult<Map<String, Object>> agentStatus(@PathVariable Long deviceId) {
        return ApiResult.ok(Map.of(
                "deviceId", deviceId,
                "online", relayHandler.isOnline(deviceId)
        ));
    }

    /** 获取在线 Agent 数量 */
    @GetMapping("/stats")
    public ApiResult<Map<String, Object>> stats() {
        return ApiResult.ok(Map.of(
                "onlineAgents", relayHandler.getOnlineAgentCount()
        ));
    }

    /** 下发命令给设备（通过 Agent） */
    @PostMapping("/{deviceId}/command")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResult<String> sendCommand(@PathVariable Long deviceId,
                                          @RequestParam String command,
                                          @RequestParam(defaultValue = "30") int timeout,
                                          @RequestBody Map<String, Object> params) {
        String result = relayHandler.sendCommandAndWait(deviceId, command, params, timeout);
        return ApiResult.ok(result);
    }

    /** 获取设备局域网内发现的设备列表 */
    @PostMapping("/{deviceId}/scan")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResult<String> scanNetwork(@PathVariable Long deviceId) {
        String result = relayHandler.sendCommandAndWait(deviceId, "scan_network", Map.of(), 60);
        return ApiResult.ok(result);
    }

    /** 断开局域网内某个用户 */
    @PostMapping("/{deviceId}/disconnect-user")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResult<String> disconnectUser(@PathVariable Long deviceId,
                                             @RequestParam String sessionId) {
        String result = relayHandler.sendCommandAndWait(deviceId, "disconnect_user",
                Map.of("sessionId", sessionId), 10);
        return ApiResult.ok(result);
    }
}
