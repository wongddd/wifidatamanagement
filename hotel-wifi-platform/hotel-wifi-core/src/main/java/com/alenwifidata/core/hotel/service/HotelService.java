package com.alenwifidata.core.hotel.service;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.hotel.mapper.HotelMapper;
import com.alenwifidata.core.hotel.model.Hotel;
import com.alenwifidata.core.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelMapper hotelMapper;

    public Page<Hotel> page(int pageNum, int pageSize, String keyword) {
        Long tenantId = TenantContext.get();
        LambdaQueryWrapper<Hotel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Hotel::getTenantId, tenantId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Hotel::getHotelName, keyword)
                             .or().like(Hotel::getHotelCode, keyword));
        }
        wrapper.orderByDesc(Hotel::getCreatedAt);
        return hotelMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Hotel getById(Long id) {
        Hotel hotel = hotelMapper.selectById(id);
        if (hotel == null) {
            throw new BusinessException(404, "酒店不存在");
        }
        return hotel;
    }

    public Hotel create(Hotel hotel) {
        hotel.setTenantId(TenantContext.get());
        hotelMapper.insert(hotel);
        return hotel;
    }

    public Hotel update(Hotel hotel) {
        getById(hotel.getId());
        hotelMapper.updateById(hotel);
        return hotel;
    }

    public void updateStatus(Long id, Integer status) {
        Hotel hotel = getById(id);
        hotel.setStatus(status);
        hotelMapper.updateById(hotel);
    }
}
