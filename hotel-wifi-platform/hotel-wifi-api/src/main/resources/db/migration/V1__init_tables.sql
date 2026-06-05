-- =============================================
-- 酒店 WiFi 数据管理计费系统 — 初始化表结构
-- =============================================

-- 1. 租户表（集团）
CREATE TABLE sys_tenant (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_name     VARCHAR(100) NOT NULL COMMENT '租户名称',
    contact_person  VARCHAR(50)  COMMENT '联系人',
    contact_phone   VARCHAR(20)  COMMENT '联系电话',
    contact_email   VARCHAR(100) COMMENT '联系邮箱',
    address         VARCHAR(500) COMMENT '地址',
    status          TINYINT DEFAULT 1 COMMENT '状态: 1启用 0停用',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- 2. 酒店表
CREATE TABLE hotel (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    hotel_name      VARCHAR(200) NOT NULL COMMENT '酒店名称',
    hotel_code      VARCHAR(50) COMMENT '酒店编码',
    address         VARCHAR(500) COMMENT '地址',
    phone           VARCHAR(20) COMMENT '联系电话',
    room_count      INT DEFAULT 0 COMMENT '房间数',
    max_online      INT DEFAULT 500 COMMENT '最大在线数',
    status          TINYINT DEFAULT 1 COMMENT '状态: 1启用 0停用',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    CONSTRAINT fk_hotel_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='酒店表';

-- 3. 系统用户表（管理后台用户）
CREATE TABLE sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    username        VARCHAR(64) NOT NULL COMMENT '用户名',
    password        VARCHAR(128) NOT NULL COMMENT '密码(BCrypt)',
    real_name       VARCHAR(50) COMMENT '真实姓名',
    phone           VARCHAR(20) COMMENT '手机号',
    email           VARCHAR(100) COMMENT '邮箱',
    role            VARCHAR(32) DEFAULT 'ADMIN' COMMENT '角色: SUPER_ADMIN/ADMIN/OPERATOR/VIEWER',
    status          TINYINT DEFAULT 1 COMMENT '状态: 1启用 0停用',
    last_login_at   DATETIME COMMENT '最后登录时间',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 4. 会员表（住客）
CREATE TABLE member (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    hotel_id        BIGINT COMMENT '酒店ID（可为空=跨店会员）',
    username        VARCHAR(64) NOT NULL COMMENT '用户名',
    password        VARCHAR(128) NOT NULL COMMENT '密码(BCrypt)',
    real_name       VARCHAR(50) COMMENT '真实姓名',
    phone           VARCHAR(20) COMMENT '手机号',
    wechat_openid   VARCHAR(128) COMMENT '微信OpenID',
    balance         DECIMAL(12,2) DEFAULT 0.00 COMMENT '账户余额(元)',
    points          INT DEFAULT 0 COMMENT '积分',
    status          TINYINT DEFAULT 1 COMMENT '状态: 1正常 0停用 -1黑名单',
    expire_at       DATETIME COMMENT '账号到期时间',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    INDEX idx_tenant_hotel (tenant_id, hotel_id),
    INDEX idx_phone (phone),
    INDEX idx_wechat_openid (wechat_openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员表';

-- 5. 套餐表
CREATE TABLE billing_package (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT NOT NULL COMMENT '租户ID',
    package_name        VARCHAR(100) NOT NULL COMMENT '套餐名称',
    description         VARCHAR(500) COMMENT '套餐描述',
    billing_type        VARCHAR(16) NOT NULL COMMENT '计费类型: TIME/TRAFFIC/HYBRID',
    duration_seconds    BIGINT DEFAULT 0 COMMENT '时长(秒): 0=不限时',
    traffic_bytes       BIGINT DEFAULT 0 COMMENT '流量(字节): 0=不限量',
    price               DECIMAL(10,2) NOT NULL COMMENT '价格(元)',
    max_cost            DECIMAL(10,2) DEFAULT 0 COMMENT '费用封顶(元): 0=不封顶',
    max_devices         INT DEFAULT 1 COMMENT '同时在线设备数',
    upload_limit_bps    BIGINT DEFAULT 0 COMMENT '上行限速(bps): 0=不限速',
    download_limit_bps  BIGINT DEFAULT 0 COMMENT '下行限速(bps): 0=不限速',
    max_connections     INT DEFAULT 0 COMMENT '最大连接数: 0=不限',
    sort_order          INT DEFAULT 0 COMMENT '排序',
    status              TINYINT DEFAULT 1 COMMENT '状态: 1启用 0停用',
    deleted             TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐表';

-- 6. 订单表
CREATE TABLE billing_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32) NOT NULL COMMENT '订单号',
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    hotel_id        BIGINT COMMENT '酒店ID',
    member_id       BIGINT NOT NULL COMMENT '会员ID',
    package_id      BIGINT NOT NULL COMMENT '套餐ID',
    amount          DECIMAL(10,2) NOT NULL COMMENT '实付金额(元)',
    pay_type        VARCHAR(16) DEFAULT 'BALANCE' COMMENT '支付方式: BALANCE/CARD/WECHAT/ALIPAY',
    status          VARCHAR(16) DEFAULT 'PENDING' COMMENT '订单状态: PENDING/PAID/CANCELLED/REFUNDED',
    remark          VARCHAR(500) COMMENT '备注',
    paid_at         DATETIME COMMENT '支付时间',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_member (member_id),
    INDEX idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 7. 上网会话表
CREATE TABLE online_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    hotel_id        BIGINT COMMENT '酒店ID',
    member_id       BIGINT NOT NULL COMMENT '会员ID',
    router_id       BIGINT NOT NULL COMMENT '路由器ID',
    session_id      VARCHAR(64) COMMENT 'MikroTik Session ID',
    mac_address     VARCHAR(17) COMMENT 'MAC地址',
    ip_address      VARCHAR(45) COMMENT 'IP地址',
    package_id      BIGINT COMMENT '使用的套餐ID',
    login_at        DATETIME NOT NULL COMMENT '上线时间',
    logout_at       DATETIME COMMENT '下线时间',
    total_bytes_in  BIGINT DEFAULT 0 COMMENT '总下载字节',
    total_bytes_out BIGINT DEFAULT 0 COMMENT '总上传字节',
    total_cost      DECIMAL(10,4) DEFAULT 0 COMMENT '总费用(元)',
    status          VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/FINISHED/KICKED',
    logout_reason   VARCHAR(100) COMMENT '下线原因',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member_active (member_id, status),
    INDEX idx_router_active (router_id, status),
    INDEX idx_login (login_at),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上网会话表';

-- 8. 流量采样记录表（按月分区，原始数据定期归档）
CREATE TABLE traffic_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT NOT NULL COMMENT '会话ID',
    member_id       BIGINT NOT NULL COMMENT '会员ID',
    router_id       BIGINT NOT NULL COMMENT '路由器ID',
    bytes_in        BIGINT NOT NULL DEFAULT 0 COMMENT '累计下载字节',
    bytes_out       BIGINT NOT NULL DEFAULT 0 COMMENT '累计上传字节',
    snapshot_at     DATETIME NOT NULL COMMENT '采样时间',
    INDEX idx_session_time (session_id, snapshot_at),
    INDEX idx_member_time (member_id, snapshot_at),
    INDEX idx_snapshot_at (snapshot_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流量采样表';

-- 9. 扣费记录表
CREATE TABLE billing_deduction (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    member_id       BIGINT NOT NULL COMMENT '会员ID',
    session_id      BIGINT NOT NULL COMMENT '会话ID',
    amount          DECIMAL(10,4) NOT NULL COMMENT '本次扣费金额(元)',
    balance_before  DECIMAL(12,2) NOT NULL COMMENT '扣费前余额(元)',
    balance_after   DECIMAL(12,2) NOT NULL COMMENT '扣费后余额(元)',
    bytes_delta     BIGINT NOT NULL DEFAULT 0 COMMENT '本次流量增量(字节)',
    deduction_type  VARCHAR(16) DEFAULT 'PREPAID' COMMENT '扣费类型: PREPAID/SETTLE/REFUND',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member_time (member_id, created_at),
    INDEX idx_session (session_id),
    INDEX idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扣费记录表';

-- 10. 路由器设备表
CREATE TABLE router_device (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    hotel_id        BIGINT NOT NULL COMMENT '酒店ID',
    device_name     VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_type     VARCHAR(32) DEFAULT 'MIKROTIK' COMMENT '设备类型: MIKROTIK/HUAWEI/H3C/CISCO',
    host            VARCHAR(255) NOT NULL COMMENT 'IP/域名',
    api_port        INT DEFAULT 8728 COMMENT 'API端口',
    api_user        VARCHAR(64) NOT NULL COMMENT 'API用户名',
    api_password    VARCHAR(256) NOT NULL COMMENT 'API密码(AES加密)',
    hotspot_server  VARCHAR(100) COMMENT 'Hotspot Server名称',
    address_pool    VARCHAR(100) COMMENT 'IP地址池',
    wan_interface   VARCHAR(32) DEFAULT 'ether1' COMMENT 'WAN口名称',
    lan_interface   VARCHAR(32) DEFAULT 'bridge1' COMMENT 'LAN口名称',
    last_sync_at    DATETIME COMMENT '最后同步时间',
    last_heartbeat  DATETIME COMMENT '最后心跳时间',
    status          VARCHAR(16) DEFAULT 'OFFLINE' COMMENT '状态: ONLINE/OFFLINE/ERROR/MAINTENANCE',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_hotel (hotel_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路由器设备表';

-- 11. 设备配置同步日志表
CREATE TABLE device_sync_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    router_id       BIGINT NOT NULL COMMENT '路由器ID',
    operation       VARCHAR(50) COMMENT '操作类型: SYNC_PROFILE/UPDATE_RATE_LIMIT/HOTSPOT_LOGIN/HOTSPOT_LOGOUT',
    request_body    TEXT COMMENT '请求内容',
    response_body   TEXT COMMENT '响应内容',
    success         TINYINT DEFAULT 1 COMMENT '是否成功: 1成功 0失败',
    error_msg       TEXT COMMENT '错误信息',
    duration_ms     INT COMMENT '耗时(毫秒)',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_router_time (router_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备同步日志表';

-- 12. 充值卡表
CREATE TABLE recharge_card (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    batch_no        VARCHAR(32) COMMENT '批次号',
    card_no         VARCHAR(32) NOT NULL COMMENT '卡号',
    card_password   VARCHAR(64) NOT NULL COMMENT '密码(加密)',
    amount          DECIMAL(10,2) NOT NULL COMMENT '面值(元)',
    package_id      BIGINT COMMENT '关联套餐ID（为空=余额充值）',
    status          VARCHAR(16) DEFAULT 'UNUSED' COMMENT '状态: UNUSED/USED/EXPIRED/REVOKED',
    used_by         BIGINT COMMENT '使用者会员ID',
    used_at         DATETIME COMMENT '使用时间',
    expire_at       DATETIME COMMENT '过期时间',
    deleted         TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_by      BIGINT COMMENT '创建人',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_card_no (card_no),
    INDEX idx_tenant_batch (tenant_id, batch_no),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值卡表';

-- ShedLock 分布式任务锁表
CREATE TABLE shedlock (
    name       VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ShedLock分布式任务锁';

-- =============================================
-- 初始种子数据
-- =============================================
INSERT INTO sys_tenant (id, tenant_name, contact_person, status) VALUES
(1, '默认集团', '系统管理员', 1);

INSERT INTO hotel (id, tenant_id, hotel_name, hotel_code, room_count) VALUES
(1, 1, '默认酒店', 'DEFAULT', 100);

-- 初始套餐数据
INSERT INTO billing_package (tenant_id, package_name, billing_type, duration_seconds, traffic_bytes, price, max_devices, download_limit_bps, upload_limit_bps) VALUES
(1, '包天套餐',      'TIME',    86400,       0,            9.90,  2, 10485760, 5242880),
(1, '包周套餐',      'TIME',    604800,      0,           49.00,  3, 20971520, 10485760),
(1, '包月套餐',      'TIME',    2592000,     0,          129.00,  3, 52428800, 20971520),
(1, '包年套餐',      'TIME',    31536000,    0,          999.00,  5, 104857600, 52428800),
(1, '1GB流量包',    'TRAFFIC', 0,           1073741824,  15.00,  1, 0, 0),
(1, '5GB流量包',    'TRAFFIC', 0,           5368709120,  50.00,  2, 0, 0),
(1, '10GB流量包',   'TRAFFIC', 0,           10737418240, 80.00,  2, 0, 0),
(1, '不限流量包月',  'TRAFFIC', 2592000,     0,          199.00,  3, 52428800, 20971520);

-- 默认管理员（密码: admin123，BCrypt 加密）
INSERT INTO sys_user (tenant_id, username, password, real_name, role) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'SUPER_ADMIN');
