package com.music.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.Result;
import com.music.entity.Merchant;
import com.music.entity.Product;
import com.music.entity.ProductCategory;
import com.music.mapper.MerchantMapper;
import com.music.service.ProductCategoryService;
import com.music.service.ProductService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.music.common.ProductStatus.ON_SALE;

@RestController
@RequestMapping("/api/product")
public class ProductController {
    @Autowired
    private ProductService productService;

    @Autowired
    private ProductCategoryService productCategoryService;

    @Resource
    private MerchantMapper merchantMapper;

    /**
     * 商品列表（分页 + 分类 + 关键词 + 类型）
     * 示例：/api/product/list?page=1&size=12&categoryId=1&keyword=周杰伦&productType=physical
     */
    @GetMapping("/list")
    public Result list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) Long merchantId) {

        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        // 只查上架商品
        qw.eq(Product::getStatus, ON_SALE);
        // 店铺
        if (merchantId != null) {
            qw.eq(Product::getMerchantId, merchantId);
        }
        // 分类
        if (categoryId != null) {
            qw.eq(Product::getCategoryId, categoryId);
        }
        // 类型
        if (productType != null && !productType.isEmpty()) {
            qw.eq(Product::getProductType, productType);
        }
        // 关键词
        if (keyword != null && !keyword.trim().isEmpty()) {
            qw.like(Product::getName, keyword.trim());
        }
        // 排序：销量倒序
        qw.orderByDesc(Product::getSales);
        Page<Product> result = productService.page(new Page<>(page, size), qw);
        return Result.ok(result);
    }

    /**
     * 店铺公开信息（商品详情页 / 店铺页使用，无需登录）
     */
    @GetMapping("/merchant/{id}")
    public Result<?> merchantInfo(@PathVariable Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null || !Integer.valueOf(1).equals(merchant.getStatus())) {
            return Result.fail("店铺不存在或已关闭");
        }
        long count = productService.count(new LambdaQueryWrapper<Product>()
                .eq(Product::getMerchantId, id)
                .eq(Product::getStatus, ON_SALE));

        Map<String, Object> data = new HashMap<>();
        data.put("id", merchant.getId());
        data.put("shopName", merchant.getShopName());
        data.put("logo", merchant.getLogo());
        data.put("description", merchant.getDescription());
        data.put("contactPhone", merchant.getContactPhone());
        data.put("productCount", count);
        return Result.ok(data);
    }

    /**
     * 商品详情
     * 示例：/api/product/detail?id=1
     */
    @GetMapping("/detail")
    public Result detail(@RequestParam Long id) {
        Product product = productService.getById(id);
        if(product == null) {
            return Result.fail("抱歉，该商品不存在");
        }
        return Result.ok(product);
    }


    /**
     * 分类列表
     */
    @GetMapping("/category/list")
    public Result categoryList(){
        LambdaQueryWrapper<ProductCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(ProductCategory::getStatus, 1);
        qw.orderByAsc(ProductCategory::getSort);
        List<ProductCategory> list = productCategoryService.list(qw);
        return Result.ok(list);
    }
}
