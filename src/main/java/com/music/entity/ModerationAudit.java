package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容审核审计记录。
 */
@Data
@TableName("moderation_audit")
public class ModerationAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** POST / COMMENT / REVIEW / NICKNAME / SIGNATURE / TEXT */
    private String contentType;

    private Long contentId;

    private Long userId;

    /** 内容 SHA-256，便于去重统计 */
    private String contentHash;

    /** 内容摘要（前 100 字） */
    private String snippet;

    /** RULE / MODEL / FALLBACK */
    private String stage;

    /** ALLOW / REVIEW / BLOCK */
    private String action;

    /** 命中的词，逗号分隔 */
    private String hits;

    /** 模型原始决策 */
    private String modelDecision;

    private Double confidence;

    private String reason;

    private Integer latencyMs;

    /** 是否异步抽检产生 */
    private Integer sampled;

    /** 异步命中后是否已自动下架 */
    private Integer handled;

    private LocalDateTime createTime;
}
