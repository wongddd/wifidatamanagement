package com.alenwifidata.common.dto;

import lombok.Data;

/**
 * 分页请求基类
 */
@Data
public class PageReq {

    private int pageNum = 1;
    private int pageSize = 10;
}
