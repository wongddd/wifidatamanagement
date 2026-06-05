package com.alenwifidata.core.tenant.mapper;

import com.alenwifidata.core.tenant.model.Tenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
