package com.alenwifidata.core.device.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.common.dto.PageReq;
import com.alenwifidata.core.device.model.RouterDevice;
import com.alenwifidata.core.device.service.RouterDeviceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class RouterDeviceController {

    private final RouterDeviceService deviceService;

    @GetMapping
    public ApiResult<Page<RouterDevice>> list(PageReq req,
                                               @RequestParam(required = false) Long hotelId,
                                               @RequestParam(required = false) String keyword) {
        return ApiResult.ok(deviceService.page(req.getPageNum(), req.getPageSize(), hotelId, keyword));
    }

    @GetMapping("/{id}")
    public ApiResult<RouterDevice> get(@PathVariable Long id) {
        return ApiResult.ok(deviceService.getById(id));
    }

    @PostMapping
    public ApiResult<RouterDevice> create(@RequestBody RouterDevice device) {
        return ApiResult.ok(deviceService.create(device));
    }

    @PutMapping("/{id}")
    public ApiResult<RouterDevice> update(@PathVariable Long id, @RequestBody RouterDevice device) {
        device.setId(id);
        return ApiResult.ok(deviceService.update(device));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/test")
    public ApiResult<Map<String, Object>> testConnection(@PathVariable Long id) {
        return ApiResult.ok(deviceService.testConnection(id));
    }

    @PostMapping("/{id}/sync")
    public ApiResult<Map<String, Object>> syncConfig(@PathVariable Long id) {
        return ApiResult.ok(deviceService.syncConfig(id));
    }
}
