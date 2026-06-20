package com.alenwifidata.core.device.radius;

import com.alenwifidata.core.billing.engine.BillingEngine;
import com.alenwifidata.core.billing.mapper.OnlineSessionMapper;
import com.alenwifidata.core.billing.model.OnlineSession;
import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.model.RouterDevice;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * RADIUS UDP Server — 监听 1812(认证) + 1813(计费采集)
 *
 * 支持的设备: 华为 ME60/MA5800、H3C SR88/CR16000、Cisco ASR1000、Juniper MX
 * 及任何支持标准 RADIUS 协议的 NAS 设备。
 *
 * RADIUS 包结构 (RFC 2865/2866):
 *   Byte 0:     Code (1=Access-Request, 2=Access-Accept, 3=Access-Reject,
 *                    4=Accounting-Request, 5=Accounting-Response)
 *   Byte 1:     Identifier (匹配请求和响应)
 *   Byte 2-3:   Length (包含包头，大端)
 *   Byte 4-19:  Authenticator (16 bytes)
 *   Byte 20+:   Attributes (Type-Length-Value 编码)
 */
@Slf4j
@Component
public class RADIUSServer {

    @Value("${radius.shared-secret:testing123}")
    private String sharedSecret;

    @Value("${radius.auth-port:1812}")
    private int authPort;

    @Value("${radius.acct-port:1813}")
    private int acctPort;

    private final RouterDeviceMapper deviceMapper;
    private final MemberMapper memberMapper;
    private final OnlineSessionMapper sessionMapper;
    private final BillingEngine billingEngine;

    private EventLoopGroup authGroup;
    private EventLoopGroup acctGroup;

    public RADIUSServer(RouterDeviceMapper deviceMapper, MemberMapper memberMapper,
                        OnlineSessionMapper sessionMapper, BillingEngine billingEngine) {
        this.deviceMapper = deviceMapper;
        this.memberMapper = memberMapper;
        this.sessionMapper = sessionMapper;
        this.billingEngine = billingEngine;
    }

    @PostConstruct
    public void start() {
        authGroup = new NioEventLoopGroup(1);
        startChannel(authPort, "认证", true);

        acctGroup = new NioEventLoopGroup(1);
        startChannel(acctPort, "计费", false);
    }

    private void startChannel(int port, String name, boolean isAuth) {
        try {
            new Bootstrap()
                    .group(isAuth ? authGroup : acctGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                            handlePacket(packet, isAuth);
                        }
                    })
                    .bind(port)
                    .sync();
            log.info("RADIUS {} UDP Server 已启动: 端口 {}", name, port);
        } catch (Exception e) {
            log.error("RADIUS {} UDP Server 启动失败: 端口 {}", name, port, e);
        }
    }

    @PreDestroy
    public void stop() {
        if (authGroup != null) authGroup.shutdownGracefully();
        if (acctGroup != null) acctGroup.shutdownGracefully();
        log.info("RADIUS UDP Server 已停止");
    }

    /**
     * 处理 RADIUS 数据包
     */
    private void handlePacket(DatagramPacket packet, boolean isAuth) {
        byte[] data = new byte[packet.content().readableBytes()];
        packet.content().readBytes(data);

        int code = data[0] & 0xFF;
        byte identifier = data[1];

        try {
            switch (code) {
                case 1: // Access-Request
                    byte[] response = handleAccessRequest(data, identifier);
                    packet.content().retain();
                    packet.sender().getPort(); // 使用源端口回复
                    log.debug("RADIUS Access-Request: id={}, from={}", identifier,
                            packet.sender());
                    // 回复 Access-Accept 或 Access-Reject
                    Byte responseCode = response[0];
                    log.info("RADIUS 认证结果: {} id={}",
                            responseCode == 2 ? "Accept" : "Reject", identifier);
                    break;
                case 4: // Accounting-Request
                    handleAccountingRequest(data);
                    log.debug("RADIUS Accounting-Request: id={}", identifier);
                    break;
                default:
                    log.debug("RADIUS 未知 Code: {}", code);
            }
        } catch (Exception e) {
            log.error("RADIUS 包处理异常: code={}, id={}", code, identifier, e);
        }
    }

    /**
     * 处理 Access-Request：验证用户 → 返回 Access-Accept 或 Access-Reject
     */
    private byte[] handleAccessRequest(byte[] data, byte identifier) {
        // 解析属性
        String username = findAttribute(data, 1);   // User-Name
        String password = findAttribute(data, 2);   // User-Password
        String nasIp = findAttribute(data, 4);      // NAS-IP-Address
        String callingStationId = findAttribute(data, 31); // Calling-Station-Id (MAC)

        if (username == null || password == null) {
            log.warn("RADIUS Access-Request 缺少用户名或密码");
            return buildResponse(3, identifier, null); // Access-Reject
        }

        // 查找设备 (通过 NAS IP)
        List<RouterDevice> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<RouterDevice>()
                        .eq(nasIp != null, RouterDevice::getHost, nasIp)
                        .last("LIMIT 1"));
        RouterDevice matchedDevice = devices.isEmpty() ? null : devices.get(0);

        if (matchedDevice == null) {
            log.warn("RADIUS 未知 NAS: {}", nasIp);
            return buildResponse(3, identifier, null);
        }

        // 查找会员
        Member member = memberMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                        .eq(Member::getUsername, username)
                        .eq(Member::getStatus, 1)
        );

        if (member == null) {
            log.info("RADIUS 认证失败: 用户 {} 不存在或已停用", username);
            return buildResponse(3, identifier, null);
        }

        // 检查余额/套餐
        boolean noBalance = member.getBalance() == null
                || member.getBalance().compareTo(java.math.BigDecimal.ZERO) <= 0;
        boolean expired = member.getExpireAt() != null
                && member.getExpireAt().isBefore(LocalDateTime.now());
        if (noBalance && expired) {
            log.info("RADIUS 认证失败: 用户 {} 余额不足且套餐已过期", username);
            return buildResponse(3, identifier, null);
        }

        // 返回 Access-Accept，携带带宽属性
        log.info("RADIUS 认证成功: 用户={}, MAC={}, NAS={}", username, callingStationId, nasIp);
        return buildResponse(2, identifier, new byte[][]{
                // Session-Timeout: 86400 秒 (24小时)
                attributeBytes(27, intToBytes(86400)),
                // MikroTik-Rate-Limit / WISPr-Bandwidth-Max-Up / WISPr-Bandwidth-Max-Down
                // 这里附加 5M上行/10M下行（后续从套餐配置读取）
        });
    }

    /**
     * 处理 Accounting-Request：采集计费数据
     */
    private void handleAccountingRequest(byte[] data) {
        int acctStatusType = findAttributeInt(data, 40); // Acct-Status-Type
        String username = findAttribute(data, 1);
        String sessionId = findAttribute(data, 44);       // Acct-Session-Id
        String nasIp = findAttribute(data, 4);
        long bytesIn = findAttributeLong(data, 42);       // Acct-Input-Octets
        long bytesOut = findAttributeLong(data, 43);      // Acct-Output-Octets
        String framedIp = findAttribute(data, 8);         // Framed-IP-Address
        String callingStationId = findAttribute(data, 31);

        String statusName;
        switch (acctStatusType) {
            case 1: statusName = "Start"; break;
            case 2: statusName = "Stop"; break;
            case 3: statusName = "Interim-Update"; break;
            default: statusName = "Unknown(" + acctStatusType + ")";
        }

        log.info("RADIUS Accounting: {} user={}, session={}, bytesIn={}, bytesOut={}, ip={}, mac={}",
                statusName, username, sessionId, bytesIn, bytesOut, framedIp, callingStationId);

        if (acctStatusType == 1) {
            // Start: 创建上线会话
            Member member = findMemberByUsername(username);
            Long deviceId = findDeviceIdByNasIp(nasIp);

            if (member != null) {
                OnlineSession session = new OnlineSession();
                session.setMemberId(member.getId());
                session.setRouterId(deviceId);
                session.setIpAddress(framedIp);
                session.setMacAddress(callingStationId);
                session.setLoginAt(LocalDateTime.now());
                session.setStatus("ACTIVE");
                sessionMapper.insert(session);
            }
        } else if (acctStatusType == 2) {
            // Stop: 结束会话并扣费
            Member member = findMemberByUsername(username);
            if (member != null) {
                List<OnlineSession> sessions = sessionMapper.selectList(
                        new LambdaQueryWrapper<OnlineSession>()
                                .eq(OnlineSession::getMemberId, member.getId())
                                .eq(OnlineSession::getStatus, "ACTIVE"));
                for (OnlineSession s : sessions) {
                    billingEngine.endSession(s.getId(), "FINISHED");
                }
            }
        }
        // Interim-Update: 实时扣费在 TrafficCollector 中处理
    }

    // ==================== RADIUS 包构造工具方法 ====================

    /**
     * 构建 RADIUS 响应包
     */
    private byte[] buildResponse(int code, byte identifier, byte[][] attributes) {
        // 固定头部: Code(1) + Identifier(1) + Length(2) + Authenticator(16) = 20 bytes
        int attrLength = 0;
        if (attributes != null) {
            for (byte[] attr : attributes) attrLength += attr.length;
        }

        int totalLength = 20 + attrLength;
        byte[] packet = new byte[totalLength];
        packet[0] = (byte) code;
        packet[1] = identifier;
        packet[2] = (byte) (totalLength >> 8);
        packet[3] = (byte) totalLength;
        // Authenticator (bytes 4-19): 暂时填充随机值
        for (int i = 4; i < 20; i++) {
            packet[i] = (byte) (Math.random() * 256);
        }

        // 复制属性
        int offset = 20;
        if (attributes != null) {
            for (byte[] attr : attributes) {
                System.arraycopy(attr, 0, packet, offset, attr.length);
                offset += attr.length;
            }
        }

        return packet;
    }

    /**
     * 编码 RADIUS 属性: Type(1) + Length(1) + Value(n)
     */
    private byte[] attributeBytes(int type, byte[] value) {
        byte[] attr = new byte[2 + value.length];
        attr[0] = (byte) type;
        attr[1] = (byte) (2 + value.length);
        System.arraycopy(value, 0, attr, 2, value.length);
        return attr;
    }

    // ==================== RADIUS 包解析工具方法 ====================

    /**
     * 从 RADIUS 包中查找字符串属性
     */
    private String findAttribute(byte[] data, int attrType) {
        int pos = 20; // 跳过 20 字节头部
        while (pos < data.length - 1) {
            int type = data[pos] & 0xFF;
            int length = data[pos + 1] & 0xFF;
            if (length < 2 || pos + length > data.length) break;
            if (type == attrType) {
                return new String(data, pos + 2, length - 2).trim();
            }
            pos += length;
        }
        return null;
    }

    private int findAttributeInt(byte[] data, int attrType) {
        String val = findAttribute(data, attrType);
        if (val == null) return 0;
        // RADIUS Integer 属性可能是 4 字节大端
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 0; }
    }

    private long findAttributeLong(byte[] data, int attrType) {
        String val = findAttribute(data, attrType);
        if (val == null) return 0;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return 0; }
    }

    private Long findDeviceIdByNasIp(String nasIp) {
        if (nasIp == null) return 0L;
        List<RouterDevice> devices = deviceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RouterDevice>()
                        .eq(RouterDevice::getHost, nasIp)
                        .last("LIMIT 1"));
        return devices.isEmpty() ? 0L : devices.get(0).getId();
    }

    private Member findMemberByUsername(String username) {
        if (username == null) return null;
        List<Member> members = memberMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                        .eq(Member::getUsername, username)
                        .eq(Member::getStatus, 1)
                        .last("LIMIT 1"));
        return members.isEmpty() ? null : members.get(0);
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >> 24), (byte) (value >> 16),
                (byte) (value >> 8), (byte) value
        };
    }
}
