package com.alenwifidata.core.hotel.controller;

import com.alenwifidata.common.dto.ApiResult;
import com.alenwifidata.common.dto.PageReq;
import com.alenwifidata.core.hotel.model.Hotel;
import com.alenwifidata.core.hotel.service.HotelService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @GetMapping
    public ApiResult<Page<Hotel>> list(PageReq req,
                                        @RequestParam(required = false) String keyword) {
        return ApiResult.ok(hotelService.page(req.getPageNum(), req.getPageSize(), keyword));
    }

    @GetMapping("/{id}")
    public ApiResult<Hotel> get(@PathVariable Long id) {
        return ApiResult.ok(hotelService.getById(id));
    }

    @PostMapping
    public ApiResult<Hotel> create(@RequestBody Hotel hotel) {
        return ApiResult.ok(hotelService.create(hotel));
    }

    @PutMapping("/{id}")
    public ApiResult<Hotel> update(@PathVariable Long id, @RequestBody Hotel hotel) {
        hotel.setId(id);
        return ApiResult.ok(hotelService.update(hotel));
    }

    @PutMapping("/{id}/status")
    public ApiResult<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        hotelService.updateStatus(id, status);
        return ApiResult.ok();
    }
}
