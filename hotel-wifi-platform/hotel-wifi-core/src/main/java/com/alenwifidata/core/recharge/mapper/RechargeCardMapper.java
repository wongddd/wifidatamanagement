package com.alenwifidata.core.recharge.mapper;

import com.alenwifidata.core.recharge.model.RechargeCard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RechargeCardMapper extends BaseMapper<RechargeCard> {

    @Select("SELECT * FROM recharge_card WHERE tenant_id = #{tenantId} AND card_no = #{cardNo} AND deleted = 0")
    RechargeCard selectByCardNo(@Param("tenantId") Long tenantId, @Param("cardNo") String cardNo);

    @Insert("<script>" +
            "INSERT INTO recharge_card (tenant_id, batch_no, card_no, card_password, amount, package_id, status, expire_at, created_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.tenantId}, #{item.batchNo}, #{item.cardNo}, #{item.cardPassword}, #{item.amount}, #{item.packageId}, #{item.status}, #{item.expireAt}, NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<RechargeCard> cards);
}
