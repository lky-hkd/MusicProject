package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.common.BizException;
import com.music.dto.CartItemDTO;
import com.music.entity.Cart;
import com.music.entity.Product;
import com.music.mapper.CartMapper;
import com.music.mapper.ProductMapper;
import com.music.service.CartService;
import com.music.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车 Service 实现
 *
 * 设计要点：
 *   1. 加购用 ON DUPLICATE KEY UPDATE 原子操作，避免并发问题
 *   2. 查购物车时把 cart + product 拼成 DTO
 *   3. 用 Redis 缓存购物车总数量，减少 count 查询
 */
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisUtil redisUtil;

    /** Redis key 前缀：购物车数量 */
    private static final String CART_COUNT_KEY="cart:count:";
    /** Redis key 前缀：购物车列表缓存（可选，先不用） */
    private static final String CART_LIST_KEY="cart:list:";
    /** 批量查询商品时，单次 IN 的最大 id 数量，避免 SQL 过长 */
    private static final int PRODUCT_QUERY_BATCH_SIZE = 500;
    /**
     * 加入购物车
     *
     * 用 ON DUPLICATE KEY UPDATE 一条 SQL 搞定：
     *   - 如果 cart 表里已有 (userId, productId)，quantity += n
     *   - 如果没有，insert 一条
     *
     * 为什么不用"先查后插"？因为并发时：
     *   线程 A 查 → 无记录 → 准备插
     *   线程 B 查 → 无记录 → 准备插
     *   两个都插 → 违反唯一索引 或 插了两条
     */
    @Override
    public void addToCart(Long userId, Long productId, Integer quantity) {
       if(quantity==null ||quantity <= 0) {
           throw new BizException("购买数量必须大于0");
       }
        Product product  = productMapper.selectById(productId);
       if(product==null) {
           throw new BizException("商品不存在");
       }
       if(product.getStatus()==null || product.getStatus()!=1) {
           throw new BizException("商品已下架");
       }
//       原子加购
        cartMapper.addOrUpdate(userId, productId, quantity);
// 清除 Redis 缓存（让下次 count 重新计算）
        redisUtil.delete(CART_COUNT_KEY+userId);
        redisUtil.delete(CART_LIST_KEY+userId);
    }

    /**
     * 查询购物车列表
     *
     * 步骤：
     *   1. 从 cart 表查该用户所有购物车项
     *   2. 对每一项，查 product 表拿商品详情
     *   3. 拼成 CartItemDTO 返回
     */
    @Override
    public List<CartItemDTO> listCart(Long userId) {
        LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
        qw.eq(Cart::getUserId, userId);
        qw.orderByDesc(Cart::getUpdateTime);// 最近加购的排前面
        List<Cart> carts = cartMapper.selectList(qw);

        if (carts.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> productIds = carts.stream()
                .map(Cart::getProductId)
                .distinct()                     // 去重（同一商品可能重复，虽然一般不会）
                .collect(Collectors.toList());

        Map<Long, Product> productMap = new HashMap<>();
        for (int i = 0; i < productIds.size(); i += PRODUCT_QUERY_BATCH_SIZE) {
            List<Long> batch = productIds.subList(
                    i, Math.min(i + PRODUCT_QUERY_BATCH_SIZE, productIds.size()));
            for (Product p : productMapper.selectBatchIds(batch)) {
                productMap.put(p.getId(), p);
            }
        }

        // 2. 拼 DTO
        List<CartItemDTO> result = new ArrayList<>();
        for(Cart cart : carts) {
            Product product = productMap.get(cart.getProductId());
            if(product==null) {
                // 商品被删了，跳过（也可以顺便清掉这条购物车记录）
                continue;
            }
            CartItemDTO dto = new CartItemDTO();
            dto.setId(cart.getId());
            dto.setProductId(cart.getProductId());
            dto.setQuantity(cart.getQuantity());
            dto.setSelected(cart.getSelected());

            dto.setName(product.getName());
            dto.setSubtitle(product.getSubtitle());
            dto.setCoverUrl(product.getCoverUrl());
            dto.setProductType(product.getProductType());
            dto.setPrice(product.getPrice());
            dto.setStock(product.getStock());

            dto.setSubtotal(product.getPrice().multiply(new BigDecimal(cart.getQuantity())));
            result.add(dto);
        }
        return result;
    }

    /**
     * 修改购物车中某项的数量
     *
     * 注意：必须校验 cart 的归属（userId 是否匹配），
     *       否则用户 A 可以改用户 B 的购物车
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, Long cartId, Integer quantity) {
        if(quantity==null ||quantity <= 0) {
            throw new BizException("数量必须大于 0");
        }
        Cart cart = cartMapper.selectById(cartId);
        if(cart==null) {
            throw new BizException("购物车项不存在");
        }

        if(!cart.getUserId().equals(userId)) {
            throw new BizException("无权操作此购物车项");
        }

        Product product = productMapper.selectById(cart.getProductId());
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BizException("商品已下架");
        }
        if(product!=null &&product.getStock()<quantity) {
            throw new BizException("库存不足，当前库存：" + product.getStock());
        }

        cart.setQuantity(quantity);
        cartMapper.updateById(cart);

        redisUtil.delete(CART_COUNT_KEY+userId);
        redisUtil.delete(CART_LIST_KEY+userId);
    }

    /**
     * 删除购物车项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFromCart(Long userId, Long cartId) {
        Cart cart = cartMapper.selectById(cartId);
        if(cart==null) {
            return ;
        }
        if(!cart.getUserId().equals(userId)) {
            throw new BizException("无权操作此购物车项");
        }
        cartMapper.deleteById(cartId);
        redisUtil.delete(CART_COUNT_KEY + userId);
        redisUtil.delete(CART_LIST_KEY + userId);
    }
    /**
     * 获取购物车总数量（角标用）
     *
     * 优化：先用 Redis 缓存，命中直接返回，未命中查数据库并写入 Redis
     *
     * 为什么用"总和"而不是"条数"？
     *   - 用户加 3 件商品，每件数量不同
     *   - 角标通常显示"总件数"，不是"商品种类数"
     *   - 例如：加购 2 个 A + 3 个 B → 角标显示 5
     */
    @Override
    public int getCartCount(Long userId) {
        String key = CART_COUNT_KEY+userId;
        // 1. 先查 Redis
        Integer cached = (Integer) redisUtil.getInteger(key);
        if(cached!=null) {return cached;}
        // 2. Redis 没有，查数据库
        LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
        qw.eq(Cart::getUserId, userId);
        List<Cart> carts = cartMapper.selectList(qw);
        // 3. 求和
        int total = 0;
        for(Cart cart : carts) {
            total += cart.getQuantity()==null ? 0 : cart.getQuantity();
        }

        // 4. 写入 Redis，缓存 5 分钟
        redisUtil.set(key, String.valueOf(total), 300);
        return total;
    }
}
