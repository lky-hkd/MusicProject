package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细表
 *
 * 存商品"快照"：下单那一刻的商品名、封面、价格，即使商品后来改价/下架，订单也保持原样
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单 id */
    private Long orderId;

    /** 下单时商品归属商家快照 */
    private Long merchantId;

    /** 商品 id */
    private Long productId;

    /** 商品名（快照） */
    private String productName;

    /** 商品封面（快照） */
    private String productCover;

    /** 商品类型快照：physical / virtual */
    private String productType;

    /** 虚拟商品类型快照：vip_month / vip_year 等 */
    private String virtualType;

    /** 虚拟商品值快照：如 VIP 天数 */
    private Integer virtualValue;

    /** 单价快照 */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 小计 = price * quantity */
    private BigDecimal subtotal;

    // ---- 订单详情展示用（不落库） ----
    @TableField(exist = false)
    private Boolean reviewed;
    @TableField(exist = false)
    private Long reviewId;
    @TableField(exist = false)
    private Integer reviewRating;
}