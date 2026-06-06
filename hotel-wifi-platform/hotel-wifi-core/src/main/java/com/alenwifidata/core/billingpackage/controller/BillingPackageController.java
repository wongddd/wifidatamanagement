package com.alenwifidata.core.billingpackage.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.common.dto.PageReq;
import com.alenwifidata.core.billingpackage.model.BillingPackage;
import com.alenwifidata.core.billingpackage.service.BillingPackageService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/packages")
@RequiredArgsConstructor
public class BillingPackageController {

    private final BillingPackageService packageService;

    @GetMapping
    public ApiResult<Page<BillingPackage>> list(PageReq req,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status) {
        return ApiResult.ok(packageService.page(req.getPageNum(), req.getPageSize(), keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResult<BillingPackage> get(@PathVariable Long id) {
        return ApiResult.ok(packageService.getById(id));
    }

    @PostMapping
    public ApiResult<BillingPackage> create(@RequestBody BillingPackage pkg) {
        return ApiResult.ok(packageService.create(pkg));
    }

    @PutMapping("/{id}")
    public ApiResult<BillingPackage> update(@PathVariable Long id, @RequestBody BillingPackage pkg) {
        pkg.setId(id);
        return ApiResult.ok(packageService.update(pkg));
    }

    @PutMapping("/{id}/status")
    public ApiResult<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        packageService.updateStatus(id, status);
        return ApiResult.ok();
    }
}
