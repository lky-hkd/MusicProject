package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户动态
 */
@Data
@TableName("post")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String content;

    /** 图片 URL 数组（JSON 字符串） */
    private String images;

    private Integer likeCount;
    private Integer commentCount;

    /** 0 正常 1 已删除 2 审核隐藏 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
