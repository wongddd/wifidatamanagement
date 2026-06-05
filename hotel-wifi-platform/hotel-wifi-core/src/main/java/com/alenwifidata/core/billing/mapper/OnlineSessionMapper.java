package com.alenwifidata.core.billing.mapper;

import com.alenwifidata.core.billing.model.OnlineSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OnlineSessionMapper extends BaseMapper<OnlineSession> {

    @Select("SELECT * FROM online_session WHERE status = 'ACTIVE' AND tenant_id = #{tenantId}")
    List<OnlineSession> selectActiveSessions(@Param("tenantId") Long tenantId);

    @Select("SELECT * FROM online_session WHERE status = 'ACTIVE' AND router_id = #{routerId}")
    List<OnlineSession> selectActiveByRouter(@Param("routerId") Long routerId);
}
