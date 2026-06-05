package com.alenwifidata.core.device.radius.impl;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.device.mapper.RouterDeviceMapper;
import com.alenwifidata.core.device.model.RouterDevice;
import com.alenwifidata.core.device.radius.RadiusRequest;
import com.alenwifidata.core.device.radius.RadiusResponse;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * RADIUS 适配器实现 — 对接标准 RADIUS 协议的 NAS 设备
 *
 * 支持设备: 华为 ME60/MA5800、H3C SR88/CR16000、思科 ASR1000、Juniper MX
 * 协议: RADIUS RFC 2865 (Authentication) + RFC 2866 (Accounting)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RadiusAdapterImpl {

    private final MemberMapper memberMapper;
    private final RouterDeviceMapper deviceMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 处理认证请求 (Access-Request)
     * RFC 2865 — 端口 1812
     */
    public RadiusResponse handleAccessRequest(RadiusRequest request) {
        String username = request.getUserName();
        String password = request.getPassword();
        String nasIp = request.getNasIpAddress();

        log.debug("RADIUS认证请求: username={}, nasIp={}, callingStationId={}",
                username, nasIp, request.getCallingStationId());

        // 查找 NAS 设备
        RouterDevice device = findDeviceByHost(nasIp);
        if (device == null) {
            log.warn("RADIUS认证拒绝: 未知NAS设备 {}", nasIp);
            return RadiusResponse.reject("Unknown NAS device");
        }

        // 查找会员
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getTenantId, device.getTenantId())
               .eq(Member::getUsername, username);
        Member member = memberMapper.selectOne(wrapper);

        if (member == null) {
            log.warn("RADIUS认证拒绝: 用户不存在 {}", username);
            return RadiusResponse.reject("User not found");
        }

        if (member.getStatus() != 1) {
            log.warn("RADIUS认证拒绝: 用户已停用 {}", username);
            return RadiusResponse.reject("User disabled");
        }

        // 检查到期时间
        if (member.getExpireAt() != null && member.getExpireAt().isBefore(java.time.LocalDateTime.now())) {
            log.warn("RADIUS认证拒绝: 套餐已过期 {}", username);
            return RadiusResponse.reject("Account expired");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, member.getPassword())) {
            log.warn("RADIUS认证拒绝: 密码错误 {}", username);
            return RadiusResponse.reject("Wrong password");
        }

        log.info("RADIUS认证通过: username={}, nasIp={}", username, nasIp);
        return RadiusResponse.accept();
    }

    /**
     * 处理计费请求 (Accounting-Request)
     * RFC 2866 — 端口 1813
     *
     * acctStatusType:
     *   Start  → 用户开始上网
     *   Stop   → 用户结束上网
     *   Interim-Update → 定期更新流量
     */
    public RadiusResponse handleAccountingRequest(RadiusRequest request) {
        String statusType = request.getAcctStatusType();
        String username = request.getUserName();
        String nasIp = request.getNasIpAddress();

        log.debug("RADIUS计费请求: username={}, statusType={}, bytesIn={}, bytesOut={}, sessionTime={}",
                username, statusType, request.getAcctInputOctets(),
                request.getAcctOutputOctets(), request.getAcctSessionTime());

        RouterDevice device = findDeviceByHost(nasIp);

        switch (statusType) {
            case RadiusRequest.ACCT_START:
                handleSessionStart(device, request);
                break;
            case RadiusRequest.ACCT_STOP:
                handleSessionStop(device, request);
                break;
            case RadiusRequest.ACCT_INTERIM:
                handleInterimUpdate(device, request);
                break;
            default:
                log.debug("RADIUS未知计费状态: {}, 已忽略", statusType);
        }

        return RadiusResponse.accountingOk();
    }

    private void handleSessionStart(RouterDevice device, RadiusRequest request) {
        log.info("RADIUS会话开始: username={}, nasIp={}, sessionId={}",
                request.getUserName(), request.getNasIpAddress(), request.getAcctSessionId());
        // TODO: 创建 online_session 记录
    }

    private void handleSessionStop(RouterDevice device, RadiusRequest request) {
        log.info("RADIUS会话结束: username={}, nasIp={}, sessionId={}, totalBytesIn={}, totalBytesOut={}",
                request.getUserName(), request.getNasIpAddress(),
                request.getAcctSessionId(), request.getAcctInputOctets(), request.getAcctOutputOctets());
        // TODO: 结束 online_session，结算费用
    }

    private void handleInterimUpdate(RouterDevice device, RadiusRequest request) {
        log.debug("RADIUS流量更新: username={}, bytesIn={}, bytesOut={}",
                request.getUserName(), request.getAcctInputOctets(), request.getAcctOutputOctets());
        // TODO: 更新 online_session 流量数据，触发计费引擎扣费
    }

    /**
     * 根据 IP 查找 NAS 设备
     */
    private RouterDevice findDeviceByHost(String nasIp) {
        if (nasIp == null) return null;
        LambdaQueryWrapper<RouterDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RouterDevice::getHost, nasIp)
               .ne(RouterDevice::getDeviceType, "MIKROTIK"); // 排除 MikroTik（用 REST API）
        return deviceMapper.selectOne(wrapper);
    }
}
