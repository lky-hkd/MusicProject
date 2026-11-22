package com.music.controller;

import com.music.common.Result;
import com.music.common.UserContext;
import com.music.dto.CartItemDTO;
import com.music.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车接口
 *
 * 所有接口都需要登录，从 session 里拿 userId
 *
 * 注意：未登录时返回 401 或 Result.fail("请先登录")，
 *       前端根据返回码跳登录页
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 加入购物车
     * 请求：POST /api/cart/add?productId=1&quantity=2
     */
    @PostMapping("/add")
    public Result<?> add(@RequestParam Long productId,
                      @RequestParam(defaultValue = "1") Integer quantity,
                         @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {

        try{
            cartService.addToCart(userId, productId, quantity);
            return Result.ok("加入购物车成功");
        }catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
    /**
     * 购物车列表
     * 请求：GET /api/cart/list
     */
    @GetMapping("/list")
    public Result<?> list(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        try{
            List<CartItemDTO> list = cartService.listCart(userId);
            return Result.ok(list);
        }catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 修改数量
     * 请求：POST /api/cart/update?cartId=1&quantity=3
     */
    @PostMapping("/update")
    public Result<?> update(@RequestParam Long cartId,
                         @RequestParam Integer quantity,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        try{
            cartService.updateQuantity(userId, cartId, quantity);
            return Result.ok("修改成功");
        }catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
    /**
     * 删除购物车项
     * 请求：POST /api/cart/remove?cartId=1
     */
    @PostMapping("/remove")
    public Result<?> remove(@RequestParam Long cartId,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        try{
            cartService.removeFromCart(userId, cartId);
            return Result.ok("删除成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 购物车总数量（导航角标用）
     * 请求：GET /api/cart/count
     *
     * 注意：未登录时返回 0（不是 fail），前端就不会显示角标
     */
    @GetMapping("/count")
    public Result<?> count(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        if (userId== null) {
            return Result.ok(0);
        }
        int count = cartService.getCartCount(userId);
        return Result.ok(count);
    }
}
