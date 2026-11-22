package com.music.service;

import com.music.dto.CartItemDTO;

import java.util.List;

public interface CartService {
    /**
     * 加入购物车
     * @param userId    用户 id
     * @param productId 商品 id
     * @param quantity  数量
     */
    void addToCart(Long userId,Long productId,Integer quantity);

    /**
     * 查询用户的购物车列表
     * @param userId 用户 id
     * @return 购物车项列表（含商品详情）
     */
    List<CartItemDTO> listCart(Long userId);

    /**
     * 修改购物车中某个商品的数量
     * @param userId  用户 id
     * @param cartId  购物车项 id
     * @param quantity 新数量
     */
    void updateQuantity(Long userId,Long cartId,Integer quantity);

    /**
     * 删除购物车中某一项
     */
    void removeFromCart(Long userId,Long cartId);

    /**
     * 获取用户购物车总数量（角标用）
     */
    int getCartCount(Long userId);
}
