package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
    /** 本店已付款订单的销售额（status in 1,2,3） */
    @Select("SELECT IFNULL(SUM(oi.subtotal),0) FROM order_item oi " +
            "JOIN `order` o ON o.id = oi.order_id " +
            "WHERE oi.merchant_id = #{merchantId} AND o.status IN (1,2,3)")
    BigDecimal sumSales(@Param("merchantId") Long merchantId);

    /** 本店待发货订单数（去重订单） */
    @Select("SELECT COUNT(DISTINCT oi.order_id) FROM order_item oi " +
            "JOIN `order` o ON o.id = oi.order_id " +
            "WHERE oi.merchant_id = #{merchantId} AND o.status = 1")
    Long countPendingShip(@Param("merchantId") Long merchantId);

    /** 按天统计销售额与订单数（已支付、未退款成功，金额按商品小计，和看板累计销售额口径一致） */
    @Select("SELECT DATE(o.pay_time) AS d, " +
            "IFNULL(SUM(oi.subtotal), 0) AS amt, " +
            "COUNT(DISTINCT o.id) AS cnt " +
            "FROM order_item oi JOIN `order` o ON o.id = oi.order_id " +
            "WHERE oi.merchant_id = #{merchantId} AND o.status IN (1,2,3) " +
            "AND IFNULL(o.refund_status, 0) <> 3 " +
            "AND o.pay_time >= #{start} AND o.pay_time < #{end} " +
            "GROUP BY DATE(o.pay_time)")
    List<Map<String, Object>> selectDailySales(@Param("merchantId") Long merchantId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /** 商品销售额 Top 5 */
    @Select("SELECT oi.product_name AS name, IFNULL(SUM(oi.subtotal), 0) AS amount " +
            "FROM order_item oi JOIN `order` o ON o.id = oi.order_id " +
            "WHERE o.merchant_id = #{merchantId} AND o.status IN (1,2,3) " +
            "AND IFNULL(o.refund_status, 0) <> 3 " +
            "AND o.pay_time >= #{start} AND o.pay_time < #{end} " +
            "GROUP BY oi.product_id, oi.product_name " +
            "ORDER BY amount DESC LIMIT 5")
    List<Map<String, Object>> selectTopProducts(@Param("merchantId") Long merchantId,
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);
}
