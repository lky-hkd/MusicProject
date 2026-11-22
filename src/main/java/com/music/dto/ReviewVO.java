package com.music.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReviewVO {
    private Long id;
    private Long userId;
    private String userName;
    private String avatar;
    private Boolean anonymous;
    private Integer rating;
    private String content;
    private List<String> images;
    private String createTime;

    private String merchantReply;
    private String merchantReplyTime;

    /** 商家评价管理里用 */
    private Long productId;
    private String productName;
    private String productCover;
}
