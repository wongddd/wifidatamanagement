package com.alenwifidata.core.member.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员实体
 */
@Data
@TableName("member")
public class Member {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long hotelId;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String wechatOpenid;
    private BigDecimal balance;
    private Integer points;
    private Integer status;
    private LocalDateTime expireAt;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
