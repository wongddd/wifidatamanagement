package com.alenwifidata.core.hotel.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 酒店实体
 */
@Data
@TableName("hotel")
public class Hotel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String hotelName;
    private String hotelCode;
    private String address;
    private String phone;
    private Integer roomCount;
    private Integer maxOnline;
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
