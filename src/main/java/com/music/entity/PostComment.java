package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论
 */
@Data
@TableName("post_comment")
public class PostComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    private String content;

    /** 回复的目标用户，为空表示直接评论 */
    private Long replyToUserId;

    /** 0 正常 1 已删除 2 审核隐藏 */
    private Integer status;

    private LocalDateTime createTime;
}
