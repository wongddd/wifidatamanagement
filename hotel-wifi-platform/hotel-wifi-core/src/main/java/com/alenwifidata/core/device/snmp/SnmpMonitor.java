package com.alenwifidata.core.device.snmp;

import com.alenwifidata.core.device.model.RouterDevice;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * SNMP 设备监控客户端
 *
 * 通过 SNMP v2c 采集路由器运行状态:
 *  - CPU 使用率 (OID: 1.3.6.1.2.1.25.3.3.1.2)
 *  - 内存使用率 (OID: 1.3.6.1.2.1.25.2.3.1.6)
 *  - 接口速率   (OID: 1.3.6.1.2.1.2.2.1.10 / .1.16)
 *  - 系统运行时间 (OID: 1.3.6.1.2.1.1.3.0)
 */
@Slf4j
@Component
public class SnmpMonitor {

    private static final String COMMUNITY = "public";
    private static final int SNMP_VERSION = SnmpConstants.version2c;
    private static final int RETRIES = 1;
    private static final int TIMEOUT = 3000;

    /** 设备监控结果 */
    public static class SnmpResult {
        public String uptime;           // 系统运行时间
        public int cpuLoad;             // CPU 使用率 (%)
        public long memUsedKb;         // 已用内存 (KB)
        public long memTotalKb;        // 总内存 (KB)
        public long wanBytesIn;        // WAN口累计下载
        public long wanBytesOut;       // WAN口累计上传
        public long lanBytesIn;        // LAN口累计下载
        public long lanBytesOut;       // LAN口累计上传
        public boolean reachable;      // 是否可达

        @Override
        public String toString() {
            return String.format("CPU:%d%%, Mem:%d/%dMB, Uptime:%s, Reachable:%s",
                    cpuLoad, memUsedKb/1024, memTotalKb/1024, uptime, reachable);
        }
    }

    /**
     * 采集设备 SNMP 数据
     */
    public SnmpResult collect(RouterDevice device) {
        SnmpResult result = new SnmpResult();
        result.reachable = false;

        Snmp snmp = null;
        try {
            TransportMapping<?> transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();

            CommunityTarget target = new CommunityTarget();
            target.setCommunity(new OctetString(COMMUNITY));
            target.setVersion(SNMP_VERSION);
            target.setAddress(new UdpAddress(device.getHost() + "/161"));
            target.setRetries(RETRIES);
            target.setTimeout(TIMEOUT);

            // 获取系统信息
            result.uptime = getAsString(snmp, target, "1.3.6.1.2.1.1.3.0");

            // CPU 负载（取第一个CPU核心的平均值）
            int cpuLoad = getAsInt(snmp, target, "1.3.6.1.2.1.25.3.3.1.2.1");
            result.cpuLoad = cpuLoad;

            // 内存
            result.memTotalKb = getAsLong(snmp, target, "1.3.6.1.2.1.25.2.3.1.5.1");
            result.memUsedKb = getAsLong(snmp, target, "1.3.6.1.2.1.25.2.3.1.6.1");

            // WAN口流量 (ether1 = 接口索引1)
            result.wanBytesIn = getAsCounter(snmp, target, "1.3.6.1.2.1.2.2.1.10.1");
            result.wanBytesOut = getAsCounter(snmp, target, "1.3.6.1.2.1.2.2.1.16.1");

            result.reachable = true;
            log.debug("SNMP采集成功: {} -> {}", device.getHost(), result);

        } catch (Exception e) {
            log.warn("SNMP采集失败 {}: {}", device.getHost(), e.getMessage());
        } finally {
            if (snmp != null) {
                try { snmp.close(); } catch (IOException ignored) {}
            }
        }

        return result;
    }

    private String getAsString(Snmp snmp, CommunityTarget target, String oid) {
        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);
            ResponseEvent event = snmp.send(pdu, target);
            if (event != null && event.getResponse() != null) {
                return event.getResponse().get(0).getVariable().toString();
            }
        } catch (Exception ignored) {}
        return "N/A";
    }

    private int getAsInt(Snmp snmp, CommunityTarget target, String oid) {
        try {
            String val = getAsString(snmp, target, oid);
            return val != null && !"N/A".equals(val) ? Integer.parseInt(val) : 0;
        } catch (Exception ignored) {}
        return 0;
    }

    private long getAsLong(Snmp snmp, CommunityTarget target, String oid) {
        try {
            String val = getAsString(snmp, target, oid);
            return val != null && !"N/A".equals(val) ? Long.parseLong(val) : 0L;
        } catch (Exception ignored) {}
        return 0L;
    }

    private long getAsCounter(Snmp snmp, CommunityTarget target, String oid) {
        return getAsLong(snmp, target, oid);
    }
}
