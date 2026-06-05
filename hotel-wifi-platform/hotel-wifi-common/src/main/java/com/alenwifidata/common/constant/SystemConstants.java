package com.alenwifidata.common.constant;

/**
 * 系统常量
 */
public final class SystemConstants {

    private SystemConstants() {}

    /** 请求头 Token Key */
    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 默认分页 */
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 缓存 Key 前缀 */
    public static final String REDIS_BALANCE_PREFIX = "billing:balance:";
    public static final String REDIS_SESSION_PREFIX = "billing:session:";
    public static final String REDIS_ONLINE_PREFIX = "billing:online:";

    /** 一天秒数 */
    public static final long DAY_SECONDS = 86400L;
    /** 一月秒数 (30天) */
    public static final long MONTH_SECONDS = 2592000L;
    /** 一年秒数 (365天) */
    public static final long YEAR_SECONDS = 31536000L;

    /** 1MB = 1048576 bytes */
    public static final long MB = 1048576L;
    /** 1GB = 1073741824 bytes */
    public static final long GB = 1073741824L;
}
