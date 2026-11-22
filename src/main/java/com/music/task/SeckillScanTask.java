package com.music.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.music.dto.SeckillMessage;
import com.music.entity.SeckillOrderTask;
import com.music.mapper.SeckillOrderTaskMapper;
import com.music.service.SeckillOrderPersistService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class SeckillScanTask {

    private static final int MAX_RETRY = 5;
    private static final int BATCH = 100;

    @Resource
    private SeckillOrderTaskMapper seckillOrderTaskMapper;
    @Resource
    private SeckillOrderPersistService seckillOrderPersistService;

    /** 每分钟扫一次：创建中且超过 2 分钟未完成的任务 */
    @Scheduled(cron = "0 * * * * ?")
    public void scanCreating(){
        // 只捞「2 分钟前创建」的任务，避免把刚插入、消费者还在处理的任务重复建单
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(2);
        List<SeckillOrderTask> list = seckillOrderTaskMapper.selectList(new LambdaQueryWrapper<SeckillOrderTask>()
                .eq(SeckillOrderTask::getStatus, SeckillOrderTask.STATUS_CREATING)
                .lt(SeckillOrderTask::getCreateTime, deadline)
                .orderByAsc(SeckillOrderTask::getId)
                .last("LIMIT" + BATCH));

        for(SeckillOrderTask t : list){
            try{
                if(t.getRetryCount()!=null&&t.getRetryCount()>=MAX_RETRY){
                    markFailed(t);
                    continue;
                }
                SeckillMessage msg= new SeckillMessage(t.getOrderNo(),t.getBizType(),t.getUserId(),t.getPayload());
               if(t.getBizType()==SeckillOrderTask.BIZ_PRODUCT){
                   seckillOrderPersistService.persistProduct(msg);
               }else{
                   seckillOrderPersistService.persistVoucher(msg);
               }
               markSuccess(t.getOrderNo());
                log.info("[补建单] 成功 orderNo={}", t.getOrderNo());
            } catch (Exception e) {
                log.error("[补建单] 失败 orderNo={}", t.getOrderNo(), e);
                incrRetry(t.getOrderNo(), e.getMessage());
            }
        }
    }

    private void markFailed(SeckillOrderTask t) {
        seckillOrderTaskMapper.update(null, new LambdaUpdateWrapper<SeckillOrderTask>()
                .eq(SeckillOrderTask::getOrderNo, t.getOrderNo())
                .set(SeckillOrderTask::getStatus, SeckillOrderTask.STATUS_FAILED));
        log.error("[补建单] 超过重试上限，转人工 orderNo={}", t.getOrderNo());
    }

    private void markSuccess(String orderNo) {
        seckillOrderTaskMapper.update(null,
                new LambdaUpdateWrapper<SeckillOrderTask>()
                        .eq(SeckillOrderTask::getOrderNo, orderNo)
                        .set(SeckillOrderTask::getStatus,SeckillOrderTask.STATUS_SUCCESS)
                        .set(SeckillOrderTask::getUpdateTime,LocalDateTime.now()));
    }

    private void incrRetry(String orderNo, String message) {
        SeckillOrderTask t = seckillOrderTaskMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrderTask>()
                        .eq(SeckillOrderTask::getOrderNo, orderNo));
        int retry = (t.getRetryCount() == null ? 0 : t.getRetryCount()) + 1;
        seckillOrderTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SeckillOrderTask>()
                .eq(SeckillOrderTask::getOrderNo, orderNo)
                .set(SeckillOrderTask::getRetryCount, retry)
                .set(SeckillOrderTask::getLastError, message == null ? null : message.substring(0, Math.min(500, message.length())))
                .set(SeckillOrderTask::getUpdateTime, LocalDateTime.now()));

    }

}
