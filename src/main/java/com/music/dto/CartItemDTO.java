package com.music.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车项 DTO
 *
 * 作用：把 cart + product 拼一起返回给前端
 *
 * 返回结构示例：
 * {
 *   "id": 1,              // cart 表的 id
 *   "productId": 3,
 *   "name": "周杰伦《七里香》CD",
 *   "subtitle": "经典专辑，全新未拆",
 *   "coverUrl": "https://...",
 *   "price": 98.00,
 *   "quantity": 2,
 *   "selected": 1,
 *   "subtotal": 196.00,   // 小计 = price * quantity
 *   "productType": "physical",
 *   "stock": 100          // 当前商品库存（前端可以校验）
 * }
 */
@Data
public class CartItemDTO {
    /** cart 表主键 */
    private Long id;

    /** 商品 id */
    private Long productId;

    /** 商品名 */
    private String name;

    /** 副标题 */
    private String subtitle;

    /** 封面图 */
    private String coverUrl;

    /** 单价 */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 是否选中 */
    private Integer selected;

    /** 小计 = price * quantity */
    private BigDecimal subtotal;

    /** 商品类型：physical / virtual */
    private String productType;

    /** 当前库存 */
    private Integer stock;
}
