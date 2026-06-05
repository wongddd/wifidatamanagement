package com.alenwifidata.core.tenant.service;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.tenant.mapper.TenantMapper;
import com.alenwifidata.core.tenant.model.Tenant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    public Page<Tenant> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Tenant::getTenantName, keyword);
        }
        wrapper.orderByDesc(Tenant::getCreatedAt);
        return tenantMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Tenant getById(Long id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(404, "租户不存在");
        }
        return tenant;
    }

    public Tenant create(Tenant tenant) {
        tenantMapper.insert(tenant);
        return tenant;
    }

    public Tenant update(Tenant tenant) {
        Tenant existing = getById(tenant.getId());
        tenantMapper.updateById(tenant);
        return tenant;
    }

    public void updateStatus(Long id, Integer status) {
        Tenant tenant = getById(id);
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
    }
}
