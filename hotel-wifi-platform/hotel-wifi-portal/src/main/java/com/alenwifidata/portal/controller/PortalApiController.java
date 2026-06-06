package com.alenwifidata.portal.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.core.auth.spi.AuthProvider;
import com.alenwifidata.core.auth.spi.AuthRequest;
import com.alenwifidata.core.auth.spi.AuthResult;
import com.alenwifidata.core.billing.engine.BillingEngine;
import com.alenwifidata.core.billing.model.BillingOrder;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.alenwifidata.core.notification.service.SmsService;
import com.alenwifidata.core.billingpackage.mapper.BillingPackageMapper;
import com.alenwifidata.core.billingpackage.model.BillingPackage;
import com.alenwifidata.core.recharge.service.RechargeCardService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Portal 住客认证 API（无需登录态，走设备 MAC/IP 识别）
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalApiController {

    private final BillingPackageMapper packageMapper;
    private final MemberMapper memberMapper;
    private final BillingEngine billingEngine;
    private final RechargeCardService cardService;
    private final SmsService smsService;
    private final List<AuthProvider> authProviders;

    // ===== 套餐展示 =====

    /** 获取可购买的套餐列表（公开） */
    @GetMapping("/packages")
    public ApiResult<List<BillingPackage>> listPackages(@RequestParam Long tenantId) {
        LambdaQueryWrapper<BillingPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingPackage::getTenantId, tenantId)
               .eq(BillingPackage::getStatus, 1)
               .orderByAsc(BillingPackage::getSortOrder);
        return ApiResult.ok(packageMapper.selectList(wrapper));
    }

    // ===== 认证入口 =====

    /** 账号密码认证 */
    @PostMapping("/auth/username")
    public ApiResult<AuthResult> authByUsername(@RequestBody AuthRequest request) {
        for (AuthProvider provider : authProviders) {
            if (provider.getType().name().equals("USERNAME_PASSWORD")) {
                return ApiResult.ok(provider.authenticate(request));
            }
        }
        return ApiResult.fail(400, "不支持的认证方式");
    }

    /** 短信认证（发送验证码） */
    @PostMapping("/auth/sms/send")
    public ApiResult<Map<String, String>> sendSms(@RequestBody AuthRequest request) {
        String code = smsService.sendVerifyCode(request.getPhone());
        return ApiResult.ok(Map.of("phone", request.getPhone(), "code", code)); // code 仅调试返回，生产去掉
    }

    /** 短信认证（验证登录） */
    @PostMapping("/auth/sms")
    public ApiResult<AuthResult> authBySms(@RequestBody AuthRequest request) {
        for (AuthProvider provider : authProviders) {
            if (provider.getType().name().equals("SMS")) {
                return ApiResult.ok(provider.authenticate(request));
            }
        }
        return ApiResult.fail(400, "短信认证暂不可用");
    }

    /** 充值卡认证 */
    @PostMapping("/auth/card")
    public ApiResult<AuthResult> authByCard(@RequestBody AuthRequest request) {
        for (AuthProvider provider : authProviders) {
            if (provider.getType().name().equals("CARD")) {
                return ApiResult.ok(provider.authenticate(request));
            }
        }
        return ApiResult.fail(400, "充值卡认证暂不可用");
    }

    // ===== 在线购买 =====

    /** 购买套餐（会员余额支付） */
    @PostMapping("/orders/buy")
    public ApiResult<BillingOrder> buyPackage(@RequestBody BuyRequest request) {
        BillingOrder order = billingEngine.createOrder(
                request.getTenantId(),
                request.getHotelId(),
                request.getMemberId(),
                request.getPackageId(),
                "BALANCE"
        );
        return ApiResult.ok(order);
    }

    // ===== 会员自助查询 =====

    /** 查询会员信息（通过手机号） */
    @GetMapping("/member/phone/{phone}")
    public ApiResult<Member> memberByPhone(@PathVariable String phone, @RequestParam Long tenantId) {
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getTenantId, tenantId)
               .eq(Member::getPhone, phone);
        Member member = memberMapper.selectOne(wrapper);
        if (member == null) {
            return ApiResult.fail(404, "会员不存在，请先注册");
        }
        member.setPassword(null); // 不返回密码
        return ApiResult.ok(member);
    }

    /** 自助注册 */
    @PostMapping("/member/register")
    public ApiResult<Member> register(@RequestBody RegisterRequest request) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return ApiResult.fail(400, "手机号不能为空");
        }
        // 检查是否已存在
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getTenantId, request.getTenantId())
               .eq(Member::getPhone, request.getPhone());
        if (memberMapper.selectOne(wrapper) != null) {
            return ApiResult.fail(400, "该手机号已注册，请直接登录");
        }
        Member member = new Member();
        member.setTenantId(request.getTenantId());
        member.setHotelId(request.getHotelId());
        member.setUsername(request.getPhone());
        member.setPhone(request.getPhone());
        member.setRealName(request.getRealName() != null ? request.getRealName() : request.getPhone());
        member.setPassword(""); // 短信认证无需密码
        memberMapper.insert(member);
        return ApiResult.ok(member);
    }

    // ===== 充值卡核销 =====

    /** 住客自助核销充值卡 */
    @PostMapping("/card/redeem")
    public ApiResult<Map<String, Object>> redeemCard(@RequestBody RedeemRequest request) {
        var member = cardService.redeem(
                request.getTenantId(),
                request.getCardNo(),
                request.getCardPassword(),
                request.getMemberId()
        );
        return ApiResult.ok(Map.of(
                "memberId", member.getId(),
                "balance", member.getBalance(),
                "expireAt", member.getExpireAt()
        ));
    }

    // ===== DTO =====

    @Data
    public static class BuyRequest {
        private Long tenantId;
        private Long hotelId;
        private Long memberId;
        private Long packageId;
    }

    @Data
    public static class RegisterRequest {
        private Long tenantId;
        private Long hotelId;
        private String phone;
        private String realName;
    }

    @Data
    public static class RedeemRequest {
        private Long tenantId;
        private String cardNo;
        private String cardPassword;
        private Long memberId;
    }
}
