package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.common.BizException;
import com.music.entity.SeckillVoucher;
import com.music.entity.Voucher;
import com.music.mapper.SeckillVoucherMapper;
import com.music.mapper.VoucherMapper;
import com.music.service.IVoucherService;
import jakarta.annotation.Resource;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IVoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVoucher(Voucher v) {
        if (v.getTitle() == null || v.getTitle().isBlank()) {
            throw new BizException("优惠券标题不能为空");
        }
        if (v.getStatus() == null) {
            v.setStatus(1);
        }
        if (v.getType() == null) {
            v.setType(0);
        }
        if (v.getType() != 0 && v.getType() != 1) {
            throw new BizException("优惠券类型非法");
        }
        v.setPayValue(v.getPayValue() == null ? 0L : v.getPayValue());
        if (v.getActualValue() == null || v.getActualValue() <= 0) {
            throw new BizException("抵扣金额必须大于0");
        }
        v.setThreshold(v.getThreshold() == null ? 0L : v.getThreshold());
        v.setValidDays(v.getValidDays() == null ? 0 : v.getValidDays());
        v.setCreateTime(LocalDateTime.now());
        v.setUpdateTime(LocalDateTime.now());

        // 秒杀券先校验再保存，避免脏数据落库
        if (v.getType() == 1) {
            Integer stock = v.getStock();
            LocalDateTime beginTime = v.getBeginTime();
            LocalDateTime endTime = v.getEndTime();
            if (stock == null || stock <= 0) {
                throw new BizException("秒杀券库存不能为空且必须大于0");
            }
            if (beginTime == null || endTime == null) {
                throw new BizException("秒杀券必须填写开始、结束时间");
            }
            if (beginTime.isAfter(endTime)) {
                throw new BizException("秒杀开始时间不能晚于结束时间");
            }
        }

        save(v);

        if (v.getType() == 1) {
            SeckillVoucher seckillVoucher = new SeckillVoucher();
            seckillVoucher.setVoucherId(v.getId());
            seckillVoucher.setStock(v.getStock());
            seckillVoucher.setBeginTime(v.getBeginTime());
            seckillVoucher.setEndTime(v.getEndTime());
            seckillVoucher.setCreateTime(LocalDateTime.now());
            seckillVoucher.setUpdateTime(LocalDateTime.now());
            seckillVoucherMapper.insert(seckillVoucher);
        }
    }

    @Override
    public List<Map<String, Object>> listAll() {
        List<Voucher> vouchers = list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Voucher>()
                .orderByDesc(Voucher::getId));
        if (vouchers.isEmpty()) {
            return List.of();
        }
        List<Long> seckillIds = vouchers.stream()
                .filter(v -> Integer.valueOf(1).equals(v.getType()))
                .map(Voucher::getId).toList();
        Map<Long, SeckillVoucher> seckillMap = seckillIds.isEmpty()
                ? Collections.emptyMap()
                : seckillVoucherMapper.selectBatchIds(seckillIds).stream()
                        .collect(Collectors.toMap(SeckillVoucher::getVoucherId, Function.identity()));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Voucher v : vouchers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("title", v.getTitle());
            m.put("subTitle", v.getSubTitle());
            m.put("rules", v.getRules());
            m.put("type", v.getType());
            m.put("status", v.getStatus());
            m.put("discount", fen2yuan(v.getActualValue()));
            m.put("threshold", fen2yuan(v.getThreshold()));
            m.put("validDays", v.getValidDays());
            m.put("createTime", v.getCreateTime());
            SeckillVoucher sv = seckillMap.get(v.getId());
            m.put("stock", sv != null ? sv.getStock() : null);
            m.put("beginTime", sv != null ? sv.getBeginTime() : null);
            m.put("endTime", sv != null ? sv.getEndTime() : null);
            list.add(m);
        }
        return list;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || status < 1 || status > 3) {
            throw new BizException("状态非法");
        }
        if (getById(id) == null) {
            throw new BizException("优惠券不存在");
        }
        update(new LambdaUpdateWrapper<Voucher>()
                .eq(Voucher::getId, id)
                .set(Voucher::getStatus, status)
                .set(Voucher::getUpdateTime, LocalDateTime.now()));
    }

    private BigDecimal fen2yuan(Long fen) {
        if (fen == null || fen <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
