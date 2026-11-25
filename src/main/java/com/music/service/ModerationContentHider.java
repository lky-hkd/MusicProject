package com.music.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.music.common.ModerationContentType;
import com.music.entity.Post;
import com.music.entity.PostComment;
import com.music.entity.ProductReview;
import com.music.mapper.PostCommentMapper;
import com.music.mapper.PostMapper;
import com.music.mapper.ProductReviewMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 异步审核命中后把内容下架（软删/隐藏）。
 * 复用各表已有的 status 语义：0=正常，1=删除/隐藏。
 */
@Slf4j
@Component
public class ModerationContentHider {

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_HIDDEN = 1;

    @Resource
    private PostMapper postMapper;

    @Resource
    private PostCommentMapper postCommentMapper;

    @Resource
    private ProductReviewMapper productReviewMapper;

    /**
     * @return 是否真的下架了内容
     */
    public boolean hide(ModerationContentType type, Long contentId) {
        if (type == null || contentId == null) {
            return false;
        }
        try {
            int rows = switch (type) {
                case POST -> postMapper.update(null, new LambdaUpdateWrapper<Post>()
                        .eq(Post::getId, contentId)
                        .eq(Post::getStatus, STATUS_NORMAL)
                        .set(Post::getStatus, STATUS_HIDDEN));
                case COMMENT -> postCommentMapper.update(null, new LambdaUpdateWrapper<PostComment>()
                        .eq(PostComment::getId, contentId)
                        .eq(PostComment::getStatus, STATUS_NORMAL)
                        .set(PostComment::getStatus, STATUS_HIDDEN));
                case REVIEW -> productReviewMapper.update(null, new LambdaUpdateWrapper<ProductReview>()
                        .eq(ProductReview::getId, contentId)
                        .eq(ProductReview::getStatus, STATUS_NORMAL)
                        .set(ProductReview::getStatus, STATUS_HIDDEN));
                default -> 0;
            };
            if (rows > 0) {
                log.warn("[moderation] 异步审核命中，已下架内容 type={} id={}", type, contentId);
                return true;
            }
        } catch (Exception e) {
            log.warn("[moderation] 下架内容失败 type={} id={} err={}", type, contentId, e.getMessage());
        }
        return false;
    }
}
