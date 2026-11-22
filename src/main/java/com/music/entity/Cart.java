package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车实体类
 *
 * 对应数据库表：cart
 *
 * 唯一约束：uk_user_product (user_id, product_id)
 *   - 同一用户同一商品只能有一条记录
 *   - 加购时用 quantity += n 更新，而不是新增
 */
@Data
@TableName("cart")
public class Cart {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id */
    private Long userId;

    /** 商品 id */
    private Long productId;

    /** 数量 */
    private Integer quantity;

    /** 是否勾选（1 选中，0 未选），用于结算 */
    private Integer selected;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}