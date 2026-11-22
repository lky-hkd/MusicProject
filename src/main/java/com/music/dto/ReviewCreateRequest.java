package com.music.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReviewCreateRequest {
    /** 订单明细 id，一个订单商品只能评价一次 */
    private Long orderItemId;
    private Integer rating;
    private String content;
    private List<String> images;
    private Boolean anonymous;
}
