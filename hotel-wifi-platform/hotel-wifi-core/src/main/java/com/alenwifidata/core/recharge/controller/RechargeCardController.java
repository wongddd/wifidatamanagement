package com.alenwifidata.core.recharge.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.core.recharge.model.RechargeCard;
import com.alenwifidata.core.recharge.service.RechargeCardService;
import com.alenwifidata.core.tenant.TenantContext;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recharge-cards")
@RequiredArgsConstructor
public class RechargeCardController {

    private final RechargeCardService cardService;

    @GetMapping
    public ApiResult<Page<RechargeCard>> list(@RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "20") int pageSize,
                                               @RequestParam(required = false) String batchNo,
                                               @RequestParam(required = false) String status) {
        return ApiResult.ok(cardService.page(pageNum, pageSize, batchNo, status));
    }

    @PostMapping("/batch")
    public ApiResult<List<RechargeCard>> batchGenerate(@RequestBody BatchGenerateRequest req) {
        return ApiResult.ok(cardService.batchGenerate(
                req.getCount(), req.getAmount(), req.getPackageId(), req.getExpireAt()));
    }

    @PostMapping("/redeem")
    public ApiResult<Map<String, Object>> redeem(@RequestBody RedeemRequest req) {
        Long tenantId = TenantContext.get();
        var member = cardService.redeem(tenantId, req.getCardNo(), req.getPassword(), req.getMemberId());
        return ApiResult.ok(Map.of(
                "memberId", member.getId(),
                "balance", member.getBalance(),
                "expireAt", member.getExpireAt()
        ));
    }

    @PutMapping("/{id}/revoke")
    public ApiResult<Void> revoke(@PathVariable Long id) {
        cardService.revoke(id);
        return ApiResult.ok();
    }

    @Data
    public static class BatchGenerateRequest {
        private int count;
        private BigDecimal amount;
        private Long packageId;
        private LocalDateTime expireAt;
    }

    @Data
    public static class RedeemRequest {
        private String cardNo;
        private String password;
        private Long memberId;
    }
}
