package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价（按订单明细逐件评价）
 */
@Data
@TableName("product_review")
public class ProductReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private Long merchantId;

    /** 1-5 星 */
    private Integer rating;
    private String content;

    /** 图片 URL 数组（JSON） */
    private String images;

    /** 0 实名 1 匿名 */
    private Integer anonymous;

    /** 0 正常 1 已隐藏 */
    private Integer status;

    private String merchantReply;
    private LocalDateTime merchantReplyTime;
    private LocalDateTime createTime;
}
