package com.alenwifidata.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计费类型
 */
@Getter
@AllArgsConstructor
public enum BillingType {

    /** 包时：包天/包月/包年 */
    TIME("包时"),
    /** 包流量：按 MB/GB */
    TRAFFIC("包流量"),
    /** 混合：包时 + 超额按流量 */
    HYBRID("混合");

    private final String description;
}
