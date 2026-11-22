package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.dto.OrderCreateRequest;
import com.music.entity.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 商品 Service 接口
 *
 * 继承 IService<Product> 后，自动拥有：
 *   - save(entity)               插入
 *   - updateById(entity)         更新
 *   - removeById(id)             删除
 *   - getById(id)                按主键查
 *   - list(wrapper)              条件查询
 *   - page(page, wrapper)        分页查询
 *
 * 商品模块目前都是标准 CRUD，无需自定义方法。
 */
public interface ProductService extends IService<Product> {

    /**
     * 判断本次下单商品中是否有热点商品。
     * 按商品维度统计最近窗口内的下单请求 QPS，任一商品超过阈值即判为热点，
     * 命中后走异步秒杀链路。统计失败时一律返回 false（走同步链路，不影响下单）。
     */
    boolean isHotProduct(List<OrderCreateRequest.Item> items);
}