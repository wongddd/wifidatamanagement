package com.alenwifidata.core.device.radius.impl;

import com.alenwifidata.core.device.radius.RadiusAdapterImpl;
import com.alenwifidata.core.device.radius.RadiusRequest;
import com.alenwifidata.core.device.radius.RadiusResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RADIUS UDP Server — 基于 Netty 监听标准 RADIUS 端口
 *
 * 端口 1812: Authentication (Access-Request)
 * 端口 1813: Accounting   (Accounting-Request)
 *
 * 注意: 本类提供简化版实现框架。
 * 生产环境建议使用 TinyRadius 库的 RadiusServer 类或对接 FreeRADIUS 作为代理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RadiusUdpServer {

    private final RadiusAdapterImpl radiusAdapter;

    private EventLoopGroup authGroup;
    private EventLoopGroup acctGroup;

    @PostConstruct
    public void start() {
        // 认证端口 1812
        authGroup = new NioEventLoopGroup(1);
        try {
            Bootstrap authBootstrap = new Bootstrap();
            authBootstrap.group(authGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new RadiusAuthHandler(radiusAdapter));
            authBootstrap.bind(1812).sync();
            log.info("RADIUS认证服务启动: 端口 1812");
        } catch (Exception e) {
            log.warn("RADIUS认证端口 1812 启动失败（非MikroTik环境可忽略）: {}", e.getMessage());
        }

        // 计费端口 1813
        acctGroup = new NioEventLoopGroup(1);
        try {
            Bootstrap acctBootstrap = new Bootstrap();
            acctBootstrap.group(acctGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new RadiusAcctHandler(radiusAdapter));
            acctBootstrap.bind(1813).sync();
            log.info("RADIUS计费服务启动: 端口 1813");
        } catch (Exception e) {
            log.warn("RADIUS计费端口 1813 启动失败（非MikroTik环境可忽略）: {}", e.getMessage());
        }

        log.info("RADIUS Server 初始化完成");
    }

    @PreDestroy
    public void stop() {
        if (authGroup != null) authGroup.shutdownGracefully();
        if (acctGroup != null) acctGroup.shutdownGracefully();
        log.info("RADIUS Server 已关闭");
    }

    /**
     * RADIUS 认证包处理器
     */
    @ChannelHandler.Sharable
    static class RadiusAuthHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final RadiusAdapterImpl adapter;
        RadiusAuthHandler(RadiusAdapterImpl adapter) { this.adapter = adapter; }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            try {
                // 简化: 从 UDP payload 解析 RADIUS 包
                // 生产环境使用 TinyRadius PacketParser
                RadiusRequest req = parseRadiusPacket(packet);
                RadiusResponse resp = adapter.handleAccessRequest(req);

                // 构造并发送 Access-Accept 或 Access-Reject
                byte[] response = buildResponsePacket(resp);
                ctx.writeAndFlush(new DatagramPacket(
                        packet.content().retain().clear().writeBytes(response),
                        packet.sender()));
            } catch (Exception e) {
                log.error("RADIUS认证处理异常", e);
            }
        }
    }

    /**
     * RADIUS 计费包处理器
     */
    @ChannelHandler.Sharable
    static class RadiusAcctHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final RadiusAdapterImpl adapter;
        RadiusAcctHandler(RadiusAdapterImpl adapter) { this.adapter = adapter; }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            try {
                RadiusRequest req = parseRadiusPacket(packet);
                adapter.handleAccountingRequest(req);
                // Accounting-Response: 简单 ACK
                ctx.writeAndFlush(new DatagramPacket(
                        packet.content().retain().clear().writeBytes(new byte[]{5, req.getAcctSessionId() != null ?
                                (byte) req.getAcctSessionId().hashCode() : 0}),
                        packet.sender()));
            } catch (Exception e) {
                log.error("RADIUS计费处理异常", e);
            }
        }
    }

    /**
     * 简易 RADIUS 包解析（生产环境替换为 TinyRadius 的 AccessRequest/AccountingRequest）
     */
    private static RadiusRequest parseRadiusPacket(DatagramPacket packet) {
        RadiusRequest req = new RadiusRequest();
        // TinyRadius: AccessRequest ar = AccessRequest.create(byteBuf);
        // ar.getUserName(), ar.getUserPassword(), etc.
        // 此处为框架占位，实际部署时引入 TinyRadius 完整解析
        return req;
    }

    private static byte[] buildResponsePacket(RadiusResponse resp) {
        // 生产环境使用 TinyRadius 构建响应包
        return new byte[]{resp.isAccessAccept() ? (byte) 2 : (byte) 3, 0};
    }
}
