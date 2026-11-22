package com.music.service.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.music.common.BizException;
import com.music.common.Role;
import com.music.dto.CommentCreateRequest;
import com.music.dto.CommentVO;
import com.music.dto.PostCreateRequest;
import com.music.dto.PostVO;
import com.music.entity.Post;
import com.music.entity.PostComment;
import com.music.entity.PostLike;
import com.music.entity.User;
import com.music.mapper.PostCommentMapper;
import com.music.mapper.PostLikeMapper;
import com.music.mapper.PostMapper;
import com.music.mapper.UserMapper;
import com.music.service.PostService;
import com.music.util.SensitiveWordFilter;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_DELETED = 1;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private PostMapper postMapper;
    @Resource
    private PostLikeMapper postLikeMapper;
    @Resource
    private PostCommentMapper postCommentMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Override
    public List<PostVO> feed(Long userId, String tab, Long lastId, int size) {
        int limit = Math.min(Math.max(size, 1), 30);
        long cursor = (lastId == null || lastId <= 0) ? Long.MAX_VALUE : lastId;
        List<Post> posts = "following".equalsIgnoreCase(tab)
                ? postMapper.selectFollowing(userId, cursor, limit)
                : postMapper.selectSquare(cursor, limit);
        return toVOList(posts, userId);
    }

    @Override
    public PostVO detail(Long userId, Long postId) {
        Post post = requirePost(postId);
        boolean liked = postLikeMapper.selectCount(new QueryWrapper<PostLike>()
                .eq("post_id", postId).eq("user_id", userId)) > 0;
        return toVO(post, userId, userMapper.selectById(post.getUserId()), liked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO create(Long userId, PostCreateRequest req) {
        if (req == null) throw new BizException("参数错误");
        String content = req.getContent() == null ? "" : req.getContent().trim();
        List<String> images = req.getImages() == null ? new ArrayList<>() : req.getImages();

        if (content.isEmpty() && images.isEmpty()) throw new BizException("内容不能为空");
        if (content.length() > 1000) throw new BizException("正文不能超过1000字");
        if (images.size() > 9) throw new BizException("最多上传9张图片");
        for (String url : images) {
            if (url == null || !url.startsWith("/uploads/post/")) {
                throw new BizException("图片地址不合法");
            }
        }
        sensitiveWordFilter.check(content);

        Post post = new Post();
        post.setUserId(userId);
        post.setContent(content);
        post.setImages(images.isEmpty() ? null : JSON.toJSONString(images));
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(STATUS_NORMAL);
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());
        postMapper.insert(post);

        return toVO(post, userId, userMapper.selectById(userId), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, int role, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) throw new BizException("动态不存在");
        if (!post.getUserId().equals(userId) && role < Role.ADMIN) {
            throw new BizException("无权删除该动态");
        }
        if (Objects.equals(post.getStatus(), STATUS_NORMAL)) {
            Post update = new Post();
            update.setId(postId);
            update.setStatus(STATUS_DELETED);
            update.setUpdateTime(LocalDateTime.now());
            postMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long userId, Long postId) {
        requirePost(postId);
        long exists = postLikeMapper.selectCount(new QueryWrapper<PostLike>()
                .eq("post_id", postId).eq("user_id", userId));
        if (exists > 0) return;

        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        like.setCreateTime(LocalDateTime.now());
        try {
            postLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            return;
        }
        postMapper.incrLike(postId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long userId, Long postId) {
        int deleted = postLikeMapper.delete(new QueryWrapper<PostLike>()
                .eq("post_id", postId).eq("user_id", userId));
        if (deleted > 0) {
            postMapper.decrLike(postId);
        }
    }

    @Override
    public List<CommentVO> comments(Long userId, Long postId, Long lastId, int size) {
        requirePost(postId);
        int limit = Math.min(Math.max(size, 1), 50);
        long cursor = (lastId == null || lastId <= 0) ? Long.MAX_VALUE : lastId;

        List<PostComment> list = postCommentMapper.selectList(new QueryWrapper<PostComment>()
                .eq("post_id", postId)
                .eq("status", STATUS_NORMAL)
                .lt("id", cursor)
                .orderByDesc("id")
                .last("limit " + limit));
        if (list.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = new HashSet<>();
        for (PostComment c : list) {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId());
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return list.stream().map(c -> toCommentVO(c, userId, userMap)).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO addComment(Long userId, Long postId, CommentCreateRequest req) {
        requirePost(postId);
        if (req == null) throw new BizException("参数错误");
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty()) throw new BizException("评论不能为空");
        if (content.length() > 500) throw new BizException("评论不能超过500字");
        sensitiveWordFilter.check(content);

        Long replyToUserId = req.getReplyToUserId();
        if (replyToUserId != null && userMapper.selectById(replyToUserId) == null) {
            replyToUserId = null;
        }

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setReplyToUserId(replyToUserId);
        comment.setStatus(STATUS_NORMAL);
        comment.setCreateTime(LocalDateTime.now());
        postCommentMapper.insert(comment);
        postMapper.incrComment(postId);

        Set<Long> ids = new HashSet<>();
        ids.add(userId);
        if (replyToUserId != null) ids.add(replyToUserId);
        Map<Long, User> userMap = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return toCommentVO(comment, userId, userMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, int role, Long commentId) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) throw new BizException("评论不存在");
        if (!comment.getUserId().equals(userId) && role < Role.ADMIN) {
            throw new BizException("无权删除该评论");
        }
        if (!Objects.equals(comment.getStatus(), STATUS_NORMAL)) return;

        PostComment update = new PostComment();
        update.setId(commentId);
        update.setStatus(STATUS_DELETED);
        postCommentMapper.updateById(update);
        postMapper.decrComment(comment.getPostId());
    }

    // ---------------- 内部工具 ----------------

    private Post requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || !Objects.equals(post.getStatus(), STATUS_NORMAL)) {
            throw new BizException("动态不存在或已删除");
        }
        return post;
    }

    private List<PostVO> toVOList(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());
        Set<Long> likedIds = postLikeMapper.selectList(new QueryWrapper<PostLike>()
                        .eq("user_id", viewerId)
                        .in("post_id", postIds))
                .stream().map(PostLike::getPostId).collect(Collectors.toSet());

        return posts.stream()
                .map(p -> toVO(p, viewerId, userMap.get(p.getUserId()), likedIds.contains(p.getId())))
                .collect(Collectors.toList());
    }

    private PostVO toVO(Post post, Long viewerId, User author, boolean liked) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setAuthorName(author == null ? "用户已注销"
                : (author.getNickname() != null ? author.getNickname() : author.getUsername()));
        vo.setAuthorAvatar(author == null ? "" : author.getAvatar());
        vo.setAuthorRole(author == null ? 0 : author.getRole());
        vo.setContent(post.getContent());
        vo.setImages(parseImages(post.getImages()));
        vo.setLikeCount(post.getLikeCount() == null ? 0 : post.getLikeCount());
        vo.setCommentCount(post.getCommentCount() == null ? 0 : post.getCommentCount());
        vo.setLiked(liked);
        vo.setMine(post.getUserId().equals(viewerId));
        vo.setCreateTime(post.getCreateTime() == null ? "" : post.getCreateTime().format(TIME_FMT));
        return vo;
    }

    private CommentVO toCommentVO(PostComment comment, Long viewerId, Map<Long, User> userMap) {
        User author = userMap.get(comment.getUserId());
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setUserId(comment.getUserId());
        vo.setAuthorName(author == null ? "用户已注销"
                : (author.getNickname() != null ? author.getNickname() : author.getUsername()));
        vo.setAuthorAvatar(author == null ? "" : author.getAvatar());
        vo.setContent(comment.getContent());
        if (comment.getReplyToUserId() != null) {
            User replyTo = userMap.get(comment.getReplyToUserId());
            vo.setReplyToName(replyTo == null ? null
                    : (replyTo.getNickname() != null ? replyTo.getNickname() : replyTo.getUsername()));
        }
        vo.setMine(comment.getUserId().equals(viewerId));
        vo.setCreateTime(comment.getCreateTime() == null ? "" : comment.getCreateTime().format(TIME_FMT));
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
