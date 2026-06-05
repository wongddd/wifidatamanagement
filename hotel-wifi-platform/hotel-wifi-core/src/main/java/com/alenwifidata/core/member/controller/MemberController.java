package com.alenwifidata.core.member.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.common.dto.PageReq;
import com.alenwifidata.core.member.model.Member;
import com.alenwifidata.core.member.service.MemberService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ApiResult<Page<Member>> list(PageReq req,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status) {
        return ApiResult.ok(memberService.page(req.getPageNum(), req.getPageSize(), keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResult<Member> get(@PathVariable Long id) {
        return ApiResult.ok(memberService.getById(id));
    }

    @PostMapping
    public ApiResult<Member> create(@RequestBody Member member) {
        return ApiResult.ok(memberService.create(member));
    }

    @PutMapping("/{id}")
    public ApiResult<Member> update(@PathVariable Long id, @RequestBody Member member) {
        member.setId(id);
        return ApiResult.ok(memberService.update(member));
    }

    @PostMapping("/{id}/recharge")
    public ApiResult<Member> recharge(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        return ApiResult.ok(memberService.recharge(id, body.get("amount")));
    }

    @PutMapping("/{id}/status")
    public ApiResult<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        memberService.updateStatus(id, status);
        return ApiResult.ok();
    }
}
