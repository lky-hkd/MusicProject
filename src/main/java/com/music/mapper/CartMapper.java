package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.Cart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 购物车 Mapper
 *
 * 继承 BaseMapper 后自动拥有：
 *   - selectById / selectList / selectPage
 *   - insert / updateById / deleteById
 *
 * 自定义方法用于"原子加购"（避免并发问题）
 */
@Mapper
public interface CartMapper extends BaseMapper<Cart> {
    /**
     * 原子加购：如果记录已存在，quantity += n；如果不存在，insert 一条
     *
     * 使用 MySQL 的 ON DUPLICATE KEY UPDATE 语法：
     *   - 依赖唯一索引 uk_user_product (user_id, product_id)
     *   - 天然原子，不会出现"并发加购导致两条记录"
     *
     * @return 影响行数
     */
    @Update("INSERT INTO cart(user_id, product_id, quantity, selected, create_time, update_time) " +
            "VALUES(#{userId}, #{productId}, #{quantity}, 1, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity}, update_time = NOW()")
    int addOrUpdate(@Param("userId") Long userId,
                    @Param("productId") Long productId,
                    @Param("quantity") Integer quantity);


}
