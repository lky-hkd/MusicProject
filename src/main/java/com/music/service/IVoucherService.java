package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.entity.Voucher;

import java.util.List;
import java.util.Map;

public interface IVoucherService extends IService<Voucher> {
    /**
     * 新增代金券（普通券/秒杀券）
     */
    void addSeckillVoucher(Voucher v);

    /**
     * 后台：优惠券列表（含秒杀库存、时间）
     */
    List<Map<String, Object>> listAll();

    /**
     * 后台：上架 / 下架 / 过期
     */
    void updateStatus(Long id, Integer status);
}
