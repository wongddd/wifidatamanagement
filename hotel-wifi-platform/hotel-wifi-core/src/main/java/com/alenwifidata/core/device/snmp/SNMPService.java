package com.alenwifidata.core.device.snmp;

import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.model.DeviceMetrics;
import com.alenwifidata.core.device.model.RouterDevice;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * SNMP 设备监控采集器 —— 通过 SNMP v2c 轮询设备 CPU/内存/接口流量
 *
 * 支持: MikroTik、华为、H3C、Cisco、Juniper 及任何支持 SNMP 的网络设备
 *
 * 关键 OID 参考:
 *   sysUpTime    : 1.3.6.1.2.1.1.3.0
 *   sysName      : 1.3.6.1.2.1.1.5.0
 *   ifDescr      : 1.3.6.1.2.1.2.2.1.2.x  (接口描述)
 *   ifInOctets   : 1.3.6.1.2.1.2.2.1.10.x (接口入流量)
 *   ifOutOctets  : 1.3.6.1.2.1.2.2.1.16.x (接口出流量)
 *   CPU load:
 *     MikroTik  : 1.3.6.1.2.1.25.3.3.1.2.x (HOST-RESOURCES-MIB)
 *     Cisco     : 1.3.6.1.4.1.9.9.109.1.1.1.1.7.x
 *   内存:
 *     total     : 1.3.6.1.4.1.2021.4.5.0 (UCD-SNMP-MIB memTotalReal)
 *     free      : 1.3.6.1.4.1.2021.4.6.0 (UCD-SNMP-MIB memAvailReal)
 */
@Slf4j
@Component
public class SNMPService {

    private final RouterDeviceMapper deviceMapper;

    @Value("${snmp.community:public}")
    private String community;

    @Value("${snmp.timeout:5000}")
    private int timeout;

    @Value("${snmp.retries:1}")
    private int retries;

    @Value("${snmp.enabled:false}")
    private boolean enabled;

    public SNMPService(RouterDeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    /**
     * 定时采集 —— 每 5 分钟
     */
    @Scheduled(fixedDelay = 300000)
    public void collectSnmpMetrics() {
        if (!enabled) {
            return;
        }

        List<RouterDevice> devices = deviceMapper.selectList(null);
        for (RouterDevice device : devices) {
            if (!"ONLINE".equals(device.getStatus())) continue;
            try {
                DeviceMetrics metrics = collectDeviceMetrics(device);
                log.debug("SNMP 采集完成: device={}, cpu={}, mem={}/{}",
                        device.getDeviceName(),
                        metrics.getCpuLoad(),
                        metrics.getMemoryUsed(),
                        metrics.getMemoryTotal());
                // TODO: 写入 snmp_snapshot 表
            } catch (Exception e) {
                log.warn("SNMP 采集失败: device={}, error={}",
                        device.getDeviceName(), e.getMessage());
            }
        }
    }

    /**
     * 采集单台设备性能指标
     */
    public DeviceMetrics collectDeviceMetrics(RouterDevice device) throws IOException {
        // 构建 SNMP Target
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(community));
        target.setAddress(GenericAddress.parse("udp:" + device.getHost() + "/161"));
        target.setVersion(SnmpConstants.version2c);
        target.setTimeout(timeout);
        target.setRetries(retries);

        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
        Snmp snmp = new Snmp(transport);
        transport.listen();

        try {
            DeviceMetrics.DeviceMetricsBuilder builder = DeviceMetrics.builder()
                    .driverName("SNMP");

            // 系统运行时间
            long uptime = snmpGet(snmp, target, "1.3.6.1.2.1.1.3.0");
            builder.uptimeSeconds(uptime > 0 ? uptime / 100 : 0); // Timeticks → 秒

            // 内存 (UCD-SNMP-MIB)
            long memTotal = snmpGet(snmp, target, "1.3.6.1.4.1.2021.4.5.0");
            long memFree = snmpGet(snmp, target, "1.3.6.1.4.1.2021.4.6.0");
            if (memTotal > 0) {
                builder.memoryTotal(memTotal * 1024);  // KB → bytes
                builder.memoryUsed((memTotal - memFree) * 1024);
            }

            // CPU (HOST-RESOURCES-MIB: hrProcessorLoad)
            long cpu = snmpGet(snmp, target, "1.3.6.1.2.1.25.3.3.1.2.1");
            if (cpu > 0) {
                builder.cpuLoad(cpu + "%");
            }

            // 接口流量 (IF-MIB)
            Map<String, Long> ifTraffic = new LinkedHashMap<>();
            for (int idx = 1; idx <= 10; idx++) {
                String ifName = snmpGetString(snmp, target, "1.3.6.1.2.1.2.2.1.2." + idx);
                if (ifName == null || ifName.isEmpty()) break;

                long inOctets = snmpGet(snmp, target, "1.3.6.1.2.1.2.2.1.10." + idx);
                long outOctets = snmpGet(snmp, target, "1.3.6.1.2.1.2.2.1.16." + idx);
                // 记录总流量（实际速率需要两次采样差值除以间隔）
                ifTraffic.put(ifName, inOctets + outOctets);
            }
            builder.interfaceTraffic(ifTraffic);

            return builder.build();

        } finally {
            snmp.close();
        }
    }

    /**
     * SNMP GET Integer/Timeticks 值
     */
    private long snmpGet(Snmp snmp, CommunityTarget target, String oid) throws IOException {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oid)));
        pdu.setType(PDU.GET);

        ResponseEvent event = snmp.send(pdu, target);
        if (event == null || event.getResponse() == null) return 0;

        VariableBinding vb = event.getResponse().get(0);
        if (vb == null || vb.getVariable() == null) return 0;

        return vb.getVariable().toLong();
    }

    /**
     * SNMP GET String 值
     */
    private String snmpGetString(Snmp snmp, CommunityTarget target, String oid) throws IOException {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oid)));
        pdu.setType(PDU.GET);

        ResponseEvent event = snmp.send(pdu, target);
        if (event == null || event.getResponse() == null) return null;

        VariableBinding vb = event.getResponse().get(0);
        if (vb == null || vb.getVariable() == null) return null;

        return vb.getVariable().toString();
    }
}
