package com.alenwifidata.core.package.service;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.package.mapper.BillingPackageMapper;
import com.alenwifidata.core.package.model.BillingPackage;
import com.alenwifidata.core.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingPackageService {

    private final BillingPackageMapper packageMapper;

    public Page<BillingPackage> page(int pageNum, int pageSize, String keyword, Integer status) {
        Long tenantId = TenantContext.get();
        LambdaQueryWrapper<BillingPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingPackage::getTenantId, tenantId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(BillingPackage::getPackageName, keyword);
        }
        if (status != null) {
            wrapper.eq(BillingPackage::getStatus, status);
        }
        wrapper.orderByAsc(BillingPackage::getSortOrder);
        return packageMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public BillingPackage getById(Long id) {
        BillingPackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BusinessException(404, "套餐不存在");
        }
        return pkg;
    }

    public BillingPackage create(BillingPackage pkg) {
        pkg.setTenantId(TenantContext.get());
        packageMapper.insert(pkg);
        return pkg;
    }

    public BillingPackage update(BillingPackage pkg) {
        getById(pkg.getId());
        packageMapper.updateById(pkg);
        return getById(pkg.getId());
    }

    public void updateStatus(Long id, Integer status) {
        BillingPackage pkg = getById(id);
        pkg.setStatus(status);
        packageMapper.updateById(pkg);
    }
}
