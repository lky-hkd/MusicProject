package com.music.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.Result;
import com.music.common.Role;
import com.music.common.UserContext;
import com.music.dto.SeckillMessage;
import com.music.dto.SeckillProductPayload;
import com.music.dto.SeckillVoucherPayload;
import com.music.entity.OrderItem;
import com.music.entity.SeckillOrderTask;
import com.music.mapper.SeckillOrderTaskMapper;
import com.music.service.Impl.RedisStockService;
import com.music.service.SeckillOrderPersistService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/admin/seckill/task")
public class AdminSeckillTaskController {
    @Resource
    private SeckillOrderTaskMapper taskMapper;
    @Resource
    private SeckillOrderPersistService seckillOrderPersistService;
    @Autowired
    private RedisStockService redisStockService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String VOUCHER_STOCK_KEY = "seckill:voucher:stock:";
    private static final String VOUCHER_USERS_KEY = "seckill:voucher:users:";

    /** 异常单列表 */
    @GetMapping("page")
    public Result<?> page(@RequestAttribute(UserContext.ROLE_ATTR) Integer role,
                          @RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "10") long size,
                          @RequestParam(required = false) String status) {
        if(role ==null || role< Role.ADMIN) return Result.fail("无权限");
        LambdaQueryWrapper<SeckillOrderTask> qw = new LambdaQueryWrapper<>();
        if(status!=null && !status.equals("")) {
            qw.eq(SeckillOrderTask::getStatus, status);
        }
        qw.orderByDesc(SeckillOrderTask::getCreateTime);
        return Result.ok(taskMapper.selectPage(new Page<>(page, size), qw));
    }

    //重试
    @PostMapping("retry/{orderNo}")
    public Result<?> retry(@RequestAttribute(UserContext.ROLE_ATTR) Integer role,
                           @PathVariable String orderNo) {
        if(role == null || role<Role.ADMIN) return Result.fail("无权限");
        SeckillOrderTask t = taskMapper.selectOne(new LambdaQueryWrapper<SeckillOrderTask>()
                .eq(SeckillOrderTask::getOrderNo, orderNo));
        if (t == null) return Result.fail("任务不存在");
        if (t.getStatus() == SeckillOrderTask.STATUS_SUCCESS) return Result.fail("已成功，无需重试");
        try{
            SeckillMessage msg = new SeckillMessage(t.getOrderNo(), t.getBizType(), t.getUserId(), t.getPayload());
            if (t.getBizType() == SeckillOrderTask.BIZ_PRODUCT){
                seckillOrderPersistService.persistProduct(msg);
            }else{
                seckillOrderPersistService.persistVoucher(msg);
            }
            taskMapper.update(null,new LambdaUpdateWrapper<SeckillOrderTask>()
                    .eq(SeckillOrderTask::getOrderNo, orderNo)
                    .set(SeckillOrderTask::getStatus, SeckillOrderTask.STATUS_SUCCESS)
                    .set(SeckillOrderTask::getUpdateTime, LocalDateTime.now()));
            return Result.ok("重试成功");
        } catch (Exception e) {
            taskMapper.update(null,new LambdaUpdateWrapper<SeckillOrderTask>()
                    .eq(SeckillOrderTask::getOrderNo, orderNo)
                    .set(SeckillOrderTask::getLastError,e.getMessage())
                    .set(SeckillOrderTask::getUpdateTime, LocalDateTime.now()));
            return Result.fail("重试失败：" + e.getMessage());
        }
    }

    /** 人工放弃：标记失败并回滚库存（由运营确认无法建单） */
    @PostMapping("abandon/{orderNo}")
    public Result<?> abandon(@PathVariable String orderNo,
                             @RequestAttribute(UserContext.ROLE_ATTR) Integer role) {
        if(role == null ||role<Role.ADMIN) return Result.fail("无权限");
        SeckillOrderTask t = taskMapper.selectOne(new LambdaQueryWrapper<SeckillOrderTask>()
                .eq(SeckillOrderTask::getOrderNo, orderNo));
        if(t==null) return Result.fail("任务不存在");
        if(t.getStatus() == SeckillOrderTask.STATUS_SUCCESS) return Result.fail("已成功，不可放弃");
        // 回滚 Redis 库存（商品单）；券单回滚券库存+用户标记
        try{
           if(t.getBizType() == SeckillOrderTask.BIZ_PRODUCT){
               SeckillProductPayload p = JSON.parseObject(t.getPayload(), SeckillProductPayload.class);
               for(OrderItem oi:p.getItems()){
                   redisStockService.rollbackStock(oi.getProductId(),oi.getQuantity());
               }
           }else{
               SeckillVoucherPayload vp=JSON.parseObject(t.getPayload(), SeckillVoucherPayload.class);
               stringRedisTemplate.opsForValue().increment(VOUCHER_STOCK_KEY + vp.getVoucherId());
               stringRedisTemplate.opsForSet().remove(VOUCHER_USERS_KEY + vp.getVoucherId(),String.valueOf(vp.getUserId()));
           }
           taskMapper.update(null,new LambdaUpdateWrapper<SeckillOrderTask>()
                   .eq(SeckillOrderTask::getOrderNo, orderNo)
                   .set(SeckillOrderTask::getStatus, SeckillOrderTask.STATUS_FAILED)
                   .set(SeckillOrderTask::getUpdateTime, LocalDateTime.now()));
            return Result.ok("已放弃并回滚库存");
        } catch (Exception e) {
            return Result.fail("放弃失败，没有回滚库存" + e.getMessage());
        }
    }
}
