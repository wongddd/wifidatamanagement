package com.alenwifidata.core.tenant.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.common.dto.PageReq;
import com.alenwifidata.core.tenant.model.Tenant;
import com.alenwifidata.core.tenant.service.TenantService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResult<Page<Tenant>> list(PageReq req,
                                         @RequestParam(required = false) String keyword) {
        return ApiResult.ok(tenantService.page(req.getPageNum(), req.getPageSize(), keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResult<Tenant> get(@PathVariable Long id) {
        return ApiResult.ok(tenantService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResult<Tenant> create(@RequestBody Tenant tenant) {
        return ApiResult.ok(tenantService.create(tenant));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResult<Tenant> update(@PathVariable Long id, @RequestBody Tenant tenant) {
        tenant.setId(id);
        return ApiResult.ok(tenantService.update(tenant));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResult<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        tenantService.updateStatus(id, status);
        return ApiResult.ok();
    }
}
