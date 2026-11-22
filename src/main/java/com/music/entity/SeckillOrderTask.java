package com.music.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seckill_order_task")
public class SeckillOrderTask {
    public static final int STATUS_CREATING = 0;
    public static final int STATUS_SUCCESS  = 1;
    public static final int STATUS_FAILED   = 2;

    public static final int BIZ_PRODUCT = 1;
    public static final int BIZ_VOUCHER = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Integer bizType;
    private Long userId;
    /** JSON 快照字符串 */
    private String payload;
    private Integer status;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
