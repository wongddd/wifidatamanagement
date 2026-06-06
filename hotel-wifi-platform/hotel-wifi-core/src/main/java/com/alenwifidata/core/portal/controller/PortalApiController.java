package com.alenwifidata.core.portal.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.core.auth.spi.AuthProvider;
import com.alenwifidata.core.auth.spi.AuthRequest;
import com.alenwifidata.core.auth.spi.AuthResult;
import com.alenwifidata.core.billing.engine.BillingEngine;
import com.alenwifidata.core.billing.model.BillingOrder;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.alenwifidata.core.notification.service.WhatsAppService;
import com.alenwifidata.core.billingpackage.mapper.BillingPackageMapper;
import com.alenwifidata.core.billingpackage.model.BillingPackage;
import com.alenwifidata.core.recharge.service.RechargeCardService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalApiController {

    private final BillingPackageMapper packageMapper;
    private final MemberMapper memberMapper;
    private final BillingEngine billingEngine;
    private final RechargeCardService cardService;
    private final WhatsAppService whatsappService;
    private final List<AuthProvider> authProviders;

    @GetMapping("/packages")
    public ApiResult<List<BillingPackage>> listPackages(@RequestParam Long tenantId) {
        LambdaQueryWrapper<BillingPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingPackage::getTenantId, tenantId)
               .eq(BillingPackage::getStatus, 1)
               .orderByAsc(BillingPackage::getSortOrder);
        return ApiResult.ok(packageMapper.selectList(wrapper));
    }

    // ===== 认证入口 =====

    @PostMapping("/auth/username")
    public ApiResult<Map<String, Object>> authByUsername(@RequestBody AuthRequest request) {
        for (AuthProvider provider : authProviders) {
            if (provider.getType().name().equals("USERNAME_PASSWORD")) {
                AuthResult result = provider.authenticate(request);
                if (!result.isSuccess()) return ApiResult.fail(401, result.getMessage());
                Long memberId = Long.parseLong(result.getUserId());
                return ApiResult.ok(buildMemberInfo(memberId));
            }
        }
        return ApiResult.fail(400, "不支持的认证方式");
    }

    @PostMapping("/auth/whatsapp/send")
    public ApiResult<Map<String, String>> sendWhatsAppCode(@RequestBody AuthRequest request) {
        String code = whatsappService.sendVerifyCode(request.getPhone());
        // 存入 Redis（5分钟过期）
        // redisTemplate.opsForValue().set("whatsapp:code:" + request.getPhone(), code, 5, TimeUnit.MINUTES);
        return ApiResult.ok(Map.of("phone", request.getPhone(), "sent", "WhatsApp验证码已发送"));
    }

    @PostMapping("/auth/whatsapp")
    public ApiResult<Map<String, Object>> authByWhatsApp(@RequestBody AuthRequest request) {
        for (AuthProvider provider : authProviders) {
            if (provider.getType().name().equals("WHATSAPP")) {
                AuthResult result = provider.authenticate(request);
                if (!result.isSuccess()) return ApiResult.fail(401, result.getMessage());
                Long memberId = Long.parseLong(result.getUserId());
                return ApiResult.ok(buildMemberInfo(memberId));
            }
        }
        return ApiResult.fail(400, "WhatsApp认证暂不可用");
    }

    @PostMapping("/auth/card")
    public ApiResult<Map<String, Object>> authByCard(@RequestBody AuthRequest request) {
        for (AuthProvider provider : authProviders) {
            if (provider.getType().name().equals("CARD")) {
                AuthResult result = provider.authenticate(request);
                if (!result.isSuccess()) return ApiResult.fail(401, result.getMessage());
                Long memberId = Long.parseLong(result.getUserId());
                return ApiResult.ok(buildMemberInfo(memberId));
            }
        }
        return ApiResult.fail(400, "充值卡认证暂不可用");
    }

    private Map<String, Object> buildMemberInfo(Long memberId) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) return Map.of();
        Map<String, Object> info = new HashMap<>();
        info.put("memberId", member.getId());
        info.put("username", member.getUsername());
        info.put("realName", member.getRealName());
        info.put("phone", member.getPhone());
        info.put("balance", member.getBalance());
        info.put("expireAt", member.getExpireAt());
        info.put("status", member.getStatus());
        return info;
    }

    // ===== 在线购买 =====

    @PostMapping("/orders/buy")
    public ApiResult<BillingOrder> buyPackage(@RequestBody BuyRequest request) {
        return ApiResult.ok(billingEngine.createOrder(
                request.getTenantId(), request.getHotelId(),
                request.getMemberId(), request.getPackageId(), "BALANCE"));
    }

    // ===== 会员自助查询 =====

    @GetMapping("/member/phone/{phone}")
    public ApiResult<Member> memberByPhone(@PathVariable String phone, @RequestParam Long tenantId) {
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getTenantId, tenantId).eq(Member::getPhone, phone);
        Member member = memberMapper.selectOne(wrapper);
        if (member == null) return ApiResult.fail(404, "会员不存在，请先注册");
        member.setPassword(null);
        return ApiResult.ok(member);
    }

    @PostMapping("/member/register")
    public ApiResult<Member> register(@RequestBody RegisterRequest request) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return ApiResult.fail(400, "手机号不能为空");
        }
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getTenantId, request.getTenantId()).eq(Member::getPhone, request.getPhone());
        if (memberMapper.selectOne(wrapper) != null) {
            return ApiResult.fail(400, "该手机号已注册");
        }
        Member member = new Member();
        member.setTenantId(request.getTenantId());
        member.setHotelId(request.getHotelId());
        member.setUsername(request.getPhone());
        member.setPhone(request.getPhone());
        member.setRealName(request.getRealName() != null ? request.getRealName() : request.getPhone());
        member.setPassword("");
        memberMapper.insert(member);
        return ApiResult.ok(member);
    }

    // ===== 充值卡核销 =====

    @PostMapping("/card/redeem")
    public ApiResult<Map<String, Object>> redeemCard(@RequestBody RedeemRequest request) {
        var member = cardService.redeem(request.getTenantId(), request.getCardNo(),
                request.getCardPassword(), request.getMemberId());
        return ApiResult.ok(Map.of("memberId", member.getId(), "balance", member.getBalance(),
                "expireAt", member.getExpireAt()));
    }

    @Data public static class BuyRequest { private Long tenantId; private Long hotelId; private Long memberId; private Long packageId; }
    @Data public static class RegisterRequest { private Long tenantId; private Long hotelId; private String phone; private String realName; }
    @Data public static class RedeemRequest { private Long tenantId; private String cardNo; private String cardPassword; private Long memberId; }
}
