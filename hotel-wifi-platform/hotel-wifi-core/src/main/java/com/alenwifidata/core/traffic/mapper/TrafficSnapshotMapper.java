package com.alenwifidata.core.traffic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TrafficSnapshotMapper extends BaseMapper<Object> {

    /** 批量插入流量快照 */
    @Insert("<script>" +
            "INSERT INTO traffic_snapshot (session_id, member_id, router_id, bytes_in, bytes_out, snapshot_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.sessionId}, #{item.memberId}, #{item.routerId}, #{item.bytesIn}, #{item.bytesOut}, NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<Map<String, Object>> snapshots);
}
