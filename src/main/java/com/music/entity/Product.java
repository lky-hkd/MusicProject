package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String subtitle;
    private String description;
    private String productType;    // physical / virtual
    private Long categoryId;

    // 虚拟商品
    private String virtualType;    // vip_month / vip_quarter / vip_year / vip_forever
    private Integer virtualValue;

    // 价格
    private BigDecimal price;
    private BigDecimal originalPrice;

    // 图片
    private String coverUrl;
    private String images;         // JSON 字符串

    // 库存 / 销量
    private Integer stock;
    private Integer sales;

    // 评价统计（冗余，评价新增/隐藏时重算）
    private Integer reviewCount;
    private BigDecimal ratingAvg;

    // 状态：0下架 1上架
    private Integer status;

    // 关联（音乐周边用）
    private Long relatedSongId;
    private String relatedSinger;

    // 时间
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private Long merchantId;
}