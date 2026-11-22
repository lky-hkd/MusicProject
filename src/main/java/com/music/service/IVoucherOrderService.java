package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.common.Result;
import com.music.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId, Long userId);

    Result createSeckillVoucher(Long voucherId, Long userId);

}
