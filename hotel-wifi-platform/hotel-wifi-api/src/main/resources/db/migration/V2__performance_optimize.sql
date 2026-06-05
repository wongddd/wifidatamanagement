-- =============================================
-- V2: 性能优化 — traffic_snapshot 按月分区
-- =============================================

-- 修改 traffic_snapshot 为分区表
ALTER TABLE traffic_snapshot
PARTITION BY RANGE (TO_DAYS(snapshot_at)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
    PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
    PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
    PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- 定期归档存储过程（手动执行）
DELIMITER //
CREATE PROCEDURE archive_old_traffic(IN archive_days INT)
BEGIN
    SET @archive_date = DATE_SUB(CURDATE(), INTERVAL archive_days DAY);
    SET @sql = CONCAT('DELETE FROM traffic_snapshot WHERE snapshot_at < ''', @archive_date, '''');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    -- 删除空分区（MySQL 8.0 支持 TRUNCATE PARTITION）
END //
DELIMITER ;

-- 查询性能索引优化
CREATE INDEX idx_online_session_status_tenant ON online_session(status, tenant_id);
CREATE INDEX idx_billing_deduction_tenant_date ON billing_deduction(tenant_id, created_at);
CREATE INDEX idx_billing_order_tenant_date ON billing_order(tenant_id, created_at);
CREATE INDEX idx_member_tenant_status ON member(tenant_id, status);

-- SNMP 监控数据表
CREATE TABLE IF NOT EXISTS snmp_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    router_id       BIGINT NOT NULL,
    cpu_load        INT DEFAULT 0 COMMENT 'CPU使用率(%)',
    mem_used_kb     BIGINT DEFAULT 0 COMMENT '已用内存(KB)',
    mem_total_kb    BIGINT DEFAULT 0 COMMENT '总内存(KB)',
    uptime          VARCHAR(50) COMMENT '运行时间',
    wan_bytes_in    BIGINT DEFAULT 0 COMMENT 'WAN口累计下载',
    wan_bytes_out   BIGINT DEFAULT 0 COMMENT 'WAN口累计上传',
    reachable       TINYINT DEFAULT 0 COMMENT '是否可达',
    snapshot_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_router_time (router_id, snapshot_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SNMP监控快照表';
