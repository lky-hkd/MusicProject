package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper
 *
 * 继承 BaseMapper<Product> 后，自动拥有：
 *   - selectById(id)             按主键查
 *   - selectList(wrapper)        条件查询
 *   - selectPage(page, wrapper)  分页查询
 *   - insert(entity)             插入
 *   - updateById(entity)         按主键更新
 *   - deleteById(id)             按主键删除
 *
 * 目前商品模块都是单表查询，无需自定义方法。
 */

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    /**
     * 原子扣库存
     *
     * 用 SQL 的 WHERE stock >= n 保证不会超卖
     * 返回影响行数：
     *   1 = 扣减成功
     *   0 = 库存不足（不满足 WHERE 条件）
     */
    @Update("UPDATE product SET stock = stock - #{quantity}, sales = sales + #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity}")
    int decreaseStock(@Param("productId") Long productId,
                      @Param("quantity") Integer quantity);

    @Update("UPDATE product set stock=stock + #{quantity},sales = sales - #{quantity} where id =#{productId}")
    void increaseStock(@Param("productId") Long productId,
                       @Param("quantity") Integer quantity);

    /**
     * 对账回写：把 DB 库存直接设为指定值（幂等，重复执行结果一致）
     */
    @Update("UPDATE product SET stock = #{stock}, update_time = NOW() WHERE id = #{productId}")
    int syncStock(@Param("productId") Long productId, @Param("stock") Integer stock);

    @Update("UPDATE product SET sales = sales + #{qty} WHERE id = #{productId}")
    int addSales(@Param("productId") Long productId,@Param("qty") Integer qty);

    @Update("UPDATE product SET sales = GREATEST(sales - #{qty}, 0) WHERE id = #{productId}")
    int reduceSales(@Param("productId") Long productId,@Param("qty") Integer qty);

}

