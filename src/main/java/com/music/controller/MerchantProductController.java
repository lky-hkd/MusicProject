package com.music.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.BizException;
import com.music.common.MerchantContext;
import com.music.common.Result;
import com.music.common.UserContext;
import com.music.dto.MerchantProductIdRequest;
import com.music.dto.MerchantProductQuery;
import com.music.dto.MerchantProductRequest;
import com.music.dto.MerchantProductStatusRequest;
import com.music.entity.OrderItem;
import com.music.entity.Product;
import com.music.mapper.OrderItemMapper;
import com.music.mapper.ProductMapper;
import com.music.service.Impl.RedisStockService;
import com.music.service.ProductService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 商家 - 商品管理
 * 所有接口第一行都通过 MerchantContext 拿到当前店铺 id，实现数据隔离。
 * 访问前缀 /api/merchant/** 已由 AuthInterceptor 校验 role >= MERCHANT。
 */
@RestController
@RequestMapping("/api/merchant/product")
public class MerchantProductController {
    @Resource
    private ProductService productService;

    @Resource
    private MerchantContext merchantContext;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    RedisStockService redisStockService;
    @Autowired
    private ProductMapper productMapper;

    /**
     * 我的商品列表（分页 + 状态 + 关键词）
     * POST /api/merchant/product/list
     * body: { "page":1, "size":10, "status":1, "keyword":"周杰伦" }
     */
    @PostMapping("/list")
    public Result<?> list(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId,
                          @RequestBody(required = false)MerchantProductQuery query) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        int page = (query == null || query.getPage() == null) ? 1 : query.getPage();
        int size = (query == null || query.getSize() == null) ? 10 : query.getSize();

        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        qw.eq(Product::getMerchantId, merchantId);
        if(query != null && query.getStatus() != null) {
            qw.eq(Product::getStatus,query.getStatus());
        }
        if(query != null && query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
            qw.like(Product::getName,query.getKeyword().trim());
        }
        qw.orderByDesc(Product::getCreateTime);
        Page<Product> result = productService.page(new Page<>(page,size),qw);
        return Result.ok(result);
    }

    /**
     * 商品详情（只能看自己的）
     * POST /api/merchant/product/detail
     * body: { "id":1 }
     */
    @PostMapping("/detail")
    public Result<?> detail(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId,
                            @RequestBody MerchantProductIdRequest request) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        if(request.getId() == null) {throw new BizException("商品id不能为空");}
        Product product = getOwnedProduct(request.getId(),merchantId);
        return Result.ok(product);
    }

    private Product getOwnedProduct(Long productId, Long merchantId) {
        Product product = productService.getById(productId);
        if(product == null){
            throw new BizException("商品不存在");
        }
        if(!merchantId.equals(product.getMerchantId())){
            throw new BizException("无权操作此商品");
        }
        return product;
    }

    /**
     * 新增 / 编辑（id 为空=新增，有值=编辑）
     * POST /api/merchant/product/save
     * body: 见 MerchantProductRequest
     */
    @PostMapping("/save")
    public Result<?> save(@RequestBody MerchantProductRequest request,
                          @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        validate(request);

        Product product;
        //1.不存在的商品
        if(request.getId() == null) {
            product = new Product();
            product.setMerchantId(merchantId);
            product.setSales(0);
            product.setCreateTime(LocalDateTime.now());
            product.setUpdateTime(LocalDateTime.now());
            copyFields(request,product);
            if(product.getStatus() == null){product.setStatus(0);}
            productService.save(product);
            redisStockService.setStockIfAbsent(product.getId(), product.getStock());
            return Result.ok(product.getId(),"商品已创建");
        }else{
            //2.已存在的商品
            product = getOwnedProduct(request.getId(),merchantId);
            Integer oldStock = product.getStock();
            copyFields(request,product);
            product.setUpdateTime(LocalDateTime.now());
            productService.updateById(product);
            if (!java.util.Objects.equals(oldStock, product.getStock())) {
                // 商家手工改过库存：以新库存覆盖 Redis，否则改动永远不会生效
                redisStockService.setStock(product.getId(), product.getStock());
            } else {
                redisStockService.setStockIfAbsent(product.getId(), product.getStock());
            }
            return Result.ok(product.getId(),"商品已更新");
        }
    }

    //基础参数校验
    private void validate(MerchantProductRequest request) {
        if(request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BizException("商品名称不能为空");
        }
        if(request.getPrice() == null || request.getPrice().signum() <= 0) {
            throw new BizException("商品价格必须大于 0");
        }
        if(request.getStock() == null || request.getStock() < 0) {
            throw new BizException("库存不能为负");
        }
        if(request.getProductType() == null || (!"physical".equals(request.getProductType())
                && !"virtual".equals(request.getProductType()))) {
            throw new BizException("商品类型只能是 physical 或 virtual");
        }
    }
    //只拷贝允许商家编辑的字段
    private void copyFields(MerchantProductRequest req, Product product) {
        product.setName(req.getName());
        product.setSubtitle(req.getSubtitle());
        product.setDescription(req.getDescription());
        product.setProductType(req.getProductType());
        product.setCategoryId(req.getCategoryId());
        product.setVirtualType(req.getVirtualType());
        product.setVirtualValue(req.getVirtualValue());
        product.setPrice(req.getPrice());
        product.setOriginalPrice(req.getOriginalPrice());
        product.setCoverUrl(req.getCoverUrl());
        product.setImages(req.getImages());
        product.setStock(req.getStock());
        if (req.getStatus() != null) {
            product.setStatus(req.getStatus());
        }
    }

    /**
     * 上架 / 下架
     * POST /api/merchant/product/status
     * body: { "id":1, "status":1 }
     */
    @PostMapping("/status")
    public Result<?> changeStatus(@RequestBody MerchantProductStatusRequest request,
                                  @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        if(request.getId() == null) {throw new BizException("商品id不能为空");}
        if(request.getStatus() == null || (request.getStatus()!=0 && request.getStatus()!=1)) {
            throw new BizException("非法状态值");
        }
        Long merchantId = merchantContext.requireMerchantId(userId);
        Product product = getOwnedProduct(request.getId(),merchantId);

        Product update = new Product();
        update.setId(product.getId());
        update.setStatus(request.getStatus());
        update.setUpdateTime(LocalDateTime.now());
        productService.updateById(update);
        if (Integer.valueOf(1).equals(request.getStatus())) {
            // 上架时兜底预热库存 key，避免 Redis 丢 key 后商品无法下单
            redisStockService.setStockIfAbsent(product.getId(), product.getStock());
        }
        return Result.ok(null,request.getStatus() ==1?"已上架" : "已下架");
    }

    /**
     * 删除商品
     * POST /api/merchant/product/delete
     * body: { "id":1 }
     * 注意：若商品已产生订单，不允许物理删除（会破坏历史订单快照关联），提示改为下架。
     */
    @PostMapping("/delete")
    public Result<?> delete(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId,
                            @RequestBody MerchantProductIdRequest request) {
        if(request.getId() == null) {throw new BizException("商品id不能为空");}

        Long merchantId = merchantContext.requireMerchantId(userId);
        Product product = getOwnedProduct(request.getId(),merchantId);

        Long usedCount = orderItemMapper.selectCount(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getProductId, product.getId()));
        if(usedCount != null && usedCount>0){
            throw new BizException("该商品已产生订单，无法删除，请改为下架");
        }
        productService.removeById(product.getId());
        return Result.ok(null,"已删除");
    }





}
