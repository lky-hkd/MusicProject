package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.music.common.Result;
import com.music.common.Role;
import com.music.common.UserContext;
import com.music.entity.ModerationPending;
import com.music.entity.Post;
import com.music.entity.PostComment;
import com.music.entity.ProductReview;
import com.music.mapper.PostCommentMapper;
import com.music.mapper.PostMapper;
import com.music.mapper.ProductReviewMapper;
import com.music.service.ModerationPendingService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 待审内容管理（管理员）。
 *
 * 可疑内容命中模型复核后落为 status=2（待审），列表不展示；
 *  nickname / signature 没有独立业务表，落 moderation_pending 待审表。
 * 管理员在这里查看、通过（status=0）或驳回（status=1）。
 * type 取值：POST / COMMENT / REVIEW / NICKNAME / SIGNATURE。
 */
@RestController
@RequestMapping("/api/admin/moderation")
public class AdminModerationController {

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_REJECTED = 1;
    private static final int STATUS_PENDING = 2;

    @Resource
    private PostMapper postMapper;
    @Resource
    private PostCommentMapper postCommentMapper;
    @Resource
    private ProductReviewMapper reviewMapper;
    @Resource
    private ModerationPendingService pendingService;

    /** 待审列表 */
    @GetMapping("pending")
    public Result<?> pending(@RequestAttribute(UserContext.ROLE_ATTR) Integer role,
                             @RequestParam(defaultValue = "POST") String type,
                             @RequestParam(defaultValue = "20") int limit) {
        if (!isAdmin(role)) {
            return Result.fail("无权限操作");
        }
        int size = Math.min(Math.max(limit, 1), 100);
        String t = type.toUpperCase(Locale.ROOT);
        List<Map<String, Object>> list = new ArrayList<>();
        if ("POST".equals(t)) {
            List<Post> posts = postMapper.selectList(new QueryWrapper<Post>()
                    .eq("status", STATUS_PENDING)
                    .orderByAsc("id")
                    .last("limit " + size));
            for (Post p : posts) {
                list.add(item("POST", p.getId(), p.getUserId(), p.getContent(), p.getCreateTime()));
            }
        } else if ("COMMENT".equals(t)) {
            List<PostComment> comments = postCommentMapper.selectList(new QueryWrapper<PostComment>()
                    .eq("status", STATUS_PENDING)
                    .orderByAsc("id")
                    .last("limit " + size));
            for (PostComment c : comments) {
                Map<String, Object> row = item("COMMENT", c.getId(), c.getUserId(), c.getContent(), c.getCreateTime());
                row.put("postId", c.getPostId());
                list.add(row);
            }
        } else if ("REVIEW".equals(t)) {
            List<ProductReview> reviews = reviewMapper.selectList(new QueryWrapper<ProductReview>()
                    .eq("status", STATUS_PENDING)
                    .orderByAsc("id")
                    .last("limit " + size));
            for (ProductReview r : reviews) {
                Map<String, Object> row = item("REVIEW", r.getId(), r.getUserId(), r.getContent(), r.getCreateTime());
                row.put("productId", r.getProductId());
                list.add(row);
            }
        } else if ("NICKNAME".equals(t) || "SIGNATURE".equals(t)) {
            // 这两个字段没有独立业务表，id 是待审记录 id
            for (ModerationPending p : pendingService.list(t, size)) {
                list.add(item(p.getContentType(), p.getId(), p.getUserId(), p.getContent(), p.getCreateTime()));
            }
        } else {
            return Result.fail("type 仅支持 POST / COMMENT / REVIEW");
        }
        return Result.ok(list);
    }

    /** 通过：恢复正常展示 */
    @PostMapping("approve")
    public Result<?> approve(@RequestAttribute(UserContext.ROLE_ATTR) Integer role,
                             @RequestParam String type,
                             @RequestParam Long id) {
        return decide(role, type, id, STATUS_NORMAL);
    }

    /** 驳回：按删除处理，不再展示 */
    @PostMapping("reject")
    public Result<?> reject(@RequestAttribute(UserContext.ROLE_ATTR) Integer role,
                            @RequestParam String type,
                            @RequestParam Long id) {
        return decide(role, type, id, STATUS_REJECTED);
    }

    private Result<?> decide(Integer role, String type, Long id, int status) {
        if (!isAdmin(role)) {
            return Result.fail("无权限操作");
        }
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
        switch (t) {
            case "POST" -> {
                Post update = new Post();
                update.setId(id);
                update.setStatus(status);
                postMapper.updateById(update);
            }
            case "COMMENT" -> {
                PostComment exist = postCommentMapper.selectById(id);
                if (exist == null) {
                    return Result.fail("评论不存在");
                }
                PostComment update = new PostComment();
                update.setId(id);
                update.setStatus(status);
                postCommentMapper.updateById(update);
                // 插入时已计入评论数，驳回需要减回去
                if (status == STATUS_REJECTED) {
                    postMapper.decrComment(exist.getPostId());
                }
            }
            case "REVIEW" -> {
                ProductReview exist = reviewMapper.selectById(id);
                if (exist == null) {
                    return Result.fail("评价不存在");
                }
                ProductReview update = new ProductReview();
                update.setId(id);
                update.setStatus(status);
                reviewMapper.updateById(update);
                // 评分统计只算正常状态，状态变化后重算
                reviewMapper.recalcStats(exist.getProductId());
            }
            case "NICKNAME", "SIGNATURE" -> {
                boolean ok = status == STATUS_NORMAL
                        ? pendingService.approve(id)
                        : pendingService.reject(id);
                if (!ok) {
                    return Result.fail("待审内容不存在或已处理");
                }
            }
            default -> {
                return Result.fail("type 仅支持 POST / COMMENT / REVIEW");
            }
        }
        return Result.ok(Boolean.TRUE);
    }

    private Map<String, Object> item(String type, Long id, Long userId, String content, Object createTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("id", id);
        row.put("userId", userId);
        row.put("content", content);
        row.put("createTime", createTime);
        return row;
    }

    private boolean isAdmin(Integer role) {
        return role != null && role >= Role.ADMIN;
    }
}
