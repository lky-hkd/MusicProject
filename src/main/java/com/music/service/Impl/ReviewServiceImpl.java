package com.music.service.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.BizException;
import com.music.common.MerchantContext;
import com.music.dto.ReviewCreateRequest;
import com.music.dto.ReviewVO;
import com.music.entity.Order;
import com.music.entity.OrderItem;
import com.music.entity.Product;
import com.music.entity.ProductReview;
import com.music.entity.User;
import com.music.mapper.OrderItemMapper;
import com.music.mapper.OrderMapper;
import com.music.mapper.ProductMapper;
import com.music.mapper.ProductReviewMapper;
import com.music.mapper.UserMapper;
import com.music.service.ReviewService;
import com.music.util.SensitiveWordFilter;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_HIDDEN = 1;

    @Resource
    private ProductReviewMapper reviewMapper;
    @Resource
    private OrderItemMapper orderItemMapper;
    @Resource
    private OrderMapper orderMapper;
    @Resource
    private ProductMapper productMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private MerchantContext merchantContext;
    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO create(Long userId, ReviewCreateRequest req) {
        if (req == null || req.getOrderItemId() == null) throw new BizException("参数错误");

        OrderItem item = orderItemMapper.selectById(req.getOrderItemId());
        if (item == null) throw new BizException("订单商品不存在");

        Order order = orderMapper.selectById(item.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) throw new BizException("无权评价该订单");
        if (!Objects.equals(order.getStatus(), 3)) throw new BizException("确认收货后才能评价");
        if (Objects.equals(order.getStatus(), 5) || Objects.equals(order.getRefundStatus(), 3)) {
            throw new BizException("已退款订单不能评价");
        }

        Product product = productMapper.selectById(item.getProductId());
        if (product == null) throw new BizException("商品不存在");

        int rating = req.getRating() == null ? 0 : req.getRating();
        if (rating < 1 || rating > 5) throw new BizException("请选择 1-5 星评分");

        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty()) throw new BizException("评价内容不能为空");
        if (content.length() > 500) throw new BizException("评价内容不能超过500字");
        sensitiveWordFilter.check(content);

        List<String> images = req.getImages() == null ? new ArrayList<>() : req.getImages();
        if (images.size() > 3) throw new BizException("最多上传3张图片");
        for (String url : images) {
            if (url == null || !url.startsWith("/uploads/review/")) throw new BizException("图片地址不合法");
        }

        long exists = reviewMapper.selectCount(new QueryWrapper<ProductReview>()
                .eq("order_item_id", item.getId()));
        if (exists > 0) throw new BizException("该商品已经评价过了");

        ProductReview review = new ProductReview();
        review.setProductId(item.getProductId());
        review.setOrderId(item.getOrderId());
        review.setOrderItemId(item.getId());
        review.setUserId(userId);
        review.setMerchantId(item.getMerchantId() != null ? item.getMerchantId() : product.getMerchantId());
        review.setRating(rating);
        review.setContent(content);
        review.setImages(images.isEmpty() ? null : JSON.toJSONString(images));
        review.setAnonymous(Boolean.TRUE.equals(req.getAnonymous()) ? 1 : 0);
        review.setStatus(STATUS_NORMAL);
        review.setCreateTime(LocalDateTime.now());
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new BizException("该商品已经评价过了");
        }

        reviewMapper.recalcStats(review.getProductId());

        Map<Long, User> users = new HashMap<>();
        User user = userMapper.selectById(userId);
        if (user != null) users.put(userId, user);
        return toVO(review, users);
    }

    @Override
    public Map<String, Object> productReviews(Long productId, int page, int size) {
        int current = Math.max(page, 1);
        int limit = Math.min(Math.max(size, 1), 30);

        Page<ProductReview> result = reviewMapper.selectPage(new Page<>(current, limit),
                new QueryWrapper<ProductReview>()
                        .eq("product_id", productId)
                        .eq("status", STATUS_NORMAL)
                        .orderByDesc("id"));

        List<ProductReview> records = result.getRecords();
        Map<Long, User> users = loadUsers(records);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.stream().map(r -> toVO(r, users)).collect(Collectors.toList()));
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("page", current);
        return data;
    }

    @Override
    public Map<String, Object> summary(Long productId) {
        List<Map<String, Object>> rows = reviewMapper.countByRating(productId);
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) distribution.put(String.valueOf(i), 0L);

        long total = 0;
        long sum = 0;
        for (Map<String, Object> row : rows) {
            Object ratingObj = row.get("rating");
            Object cntObj = row.get("cnt");
            if (ratingObj == null || cntObj == null) continue;
            int rating = ((Number) ratingObj).intValue();
            long cnt = ((Number) cntObj).longValue();
            total += cnt;
            sum += (long) rating * cnt;
            distribution.put(String.valueOf(rating), cnt);
        }

        double avg = total == 0 ? 5.0 : BigDecimal.valueOf((double) sum / total)
                .setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("avg", avg);
        data.put("count", total);
        data.put("distribution", distribution);
        return data;
    }

    @Override
    public Map<String, Object> merchantReviews(Long userId, int page, int size) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        int current = Math.max(page, 1);
        int limit = Math.min(Math.max(size, 1), 30);

        Page<ProductReview> result = reviewMapper.selectPage(new Page<>(current, limit),
                new QueryWrapper<ProductReview>()
                        .eq("merchant_id", merchantId)
                        .eq("status", STATUS_NORMAL)
                        .orderByDesc("id"));

        List<ProductReview> records = result.getRecords();
        Map<Long, User> users = loadUsers(records);

        Set<Long> productIds = records.stream().map(ProductReview::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<ReviewVO> list = records.stream().map(r -> {
            ReviewVO vo = toVO(r, users);
            Product p = products.get(r.getProductId());
            if (p != null) {
                vo.setProductId(p.getId());
                vo.setProductName(p.getName());
                vo.setProductCover(p.getCoverUrl());
            }
            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("page", current);
        return data;
    }

    @Override
    public void merchantReply(Long userId, Long reviewId, String content) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) throw new BizException("回复内容不能为空");
        if (text.length() > 500) throw new BizException("回复不能超过500字");
        sensitiveWordFilter.check(text);

        ProductReview review = reviewMapper.selectById(reviewId);
        if (review == null || !Objects.equals(review.getStatus(), STATUS_NORMAL)) {
            throw new BizException("评价不存在");
        }
        if (!merchantId.equals(review.getMerchantId())) throw new BizException("无权回复该评价");

        ProductReview update = new ProductReview();
        update.setId(reviewId);
        update.setMerchantReply(text);
        update.setMerchantReplyTime(LocalDateTime.now());
        reviewMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hide(Long reviewId) {
        ProductReview review = reviewMapper.selectById(reviewId);
        if (review == null) throw new BizException("评价不存在");
        if (Objects.equals(review.getStatus(), STATUS_HIDDEN)) return;

        ProductReview update = new ProductReview();
        update.setId(reviewId);
        update.setStatus(STATUS_HIDDEN);
        reviewMapper.updateById(update);
        reviewMapper.recalcStats(review.getProductId());
    }

    @Override
    public Map<Long, ProductReview> mapByOrderItemIds(Collection<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) return Collections.emptyMap();
        return reviewMapper.selectList(new QueryWrapper<ProductReview>()
                        .in("order_item_id", orderItemIds))
                .stream().collect(Collectors.toMap(ProductReview::getOrderItemId, r -> r));
    }

    // ---------------- 内部工具 ----------------

    private Map<Long, User> loadUsers(List<ProductReview> records) {
        Set<Long> userIds = records.stream().map(ProductReview::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private ReviewVO toVO(ProductReview review, Map<Long, User> users) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setImages(parseImages(review.getImages()));
        vo.setCreateTime(review.getCreateTime() == null ? "" : review.getCreateTime().format(TIME_FMT));
        vo.setMerchantReply(review.getMerchantReply());
        vo.setMerchantReplyTime(review.getMerchantReplyTime() == null ? ""
                : review.getMerchantReplyTime().format(TIME_FMT));

        boolean anonymous = Objects.equals(review.getAnonymous(), 1);
        vo.setAnonymous(anonymous);
        if (anonymous) {
            vo.setUserName("匿名用户");
            vo.setAvatar("");
        } else {
            User user = users.get(review.getUserId());
            vo.setUserId(review.getUserId());
            vo.setUserName(user == null ? "用户已注销"
                    : (user.getNickname() != null ? user.getNickname() : user.getUsername()));
            vo.setAvatar(user == null ? "" : user.getAvatar());
        }
        return vo;
    }

    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            List<String> list = JSON.parseArray(json, String.class);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
