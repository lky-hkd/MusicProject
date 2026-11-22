package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.ProductReview;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    /** 重算商品的评价数量和平均分（只统计正常状态） */
    @Update("UPDATE product p SET " +
            "review_count = (SELECT COUNT(*) FROM product_review r WHERE r.product_id = p.id AND r.status = 0), " +
            "rating_avg = COALESCE((SELECT ROUND(AVG(r.rating), 2) FROM product_review r WHERE r.product_id = p.id AND r.status = 0), 5.00) " +
            "WHERE p.id = #{productId}")
    int recalcStats(@Param("productId") Long productId);

    /** 星级分布 */
    @Select("SELECT rating, COUNT(*) AS cnt FROM product_review " +
            "WHERE product_id = #{productId} AND status = 0 GROUP BY rating")
    List<Map<String, Object>> countByRating(@Param("productId") Long productId);
}
