package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审内容：不落业务表的场景（昵称、个性签名）先存这里，管理员通过后再写回用户表。
 */
@Data
@TableName("moderation_pending")
public class ModerationPending {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** NICKNAME / SIGNATURE */
    private String contentType;

    private Long userId;

    private String content;

    /** 0 待审 / 1 通过 / 2 驳回 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
