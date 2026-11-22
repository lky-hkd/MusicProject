package com.music.service;

import com.music.dto.CommentCreateRequest;
import com.music.dto.CommentVO;
import com.music.dto.PostCreateRequest;
import com.music.dto.PostVO;

import java.util.List;

public interface PostService {

    /** 动态流：square 广场 / following 关注 */
    List<PostVO> feed(Long userId, String tab, Long lastId, int size);

    PostVO detail(Long userId, Long postId);

    PostVO create(Long userId, PostCreateRequest req);

    /** 软删；作者本人或管理员（role>=2）可删 */
    void delete(Long userId, int role, Long postId);

    void like(Long userId, Long postId);

    void unlike(Long userId, Long postId);

    List<CommentVO> comments(Long userId, Long postId, Long lastId, int size);

    CommentVO addComment(Long userId, Long postId, CommentCreateRequest req);

    /** 软删评论；作者本人或管理员可删 */
    void deleteComment(Long userId, int role, Long commentId);
}
