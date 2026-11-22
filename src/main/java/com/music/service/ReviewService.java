package com.music.service;

import com.music.dto.ReviewCreateRequest;
import com.music.dto.ReviewVO;
import com.music.entity.ProductReview;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ReviewService {

    /** 提交评价（逐订单商品） */
    ReviewVO create(Long userId, ReviewCreateRequest req);

    /** 商品评价列表（公开） */
    Map<String, Object> productReviews(Long productId, int page, int size);

    /** 商品评分汇总（公开） */
    Map<String, Object> summary(Long productId);

    /** 商家评价管理 */
    Map<String, Object> merchantReviews(Long userId, int page, int size);

    /** 商家回复 */
    void merchantReply(Long userId, Long reviewId, String content);

    /** 管理员隐藏违规评价 */
    void hide(Long reviewId);

    /** 订单详情用：按订单明细批量取评价 */
    Map<Long, ProductReview> mapByOrderItemIds(Collection<Long> orderItemIds);
}
