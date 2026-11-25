package com.music.dto;

import com.music.common.ModerationContentType;
import lombok.Builder;
import lombok.Data;

/**
 * 审核上下文：带上内容归属，便于审计和异步抽检命中后下架。
 */
@Data
@Builder
public class ModerationContext {

    private ModerationContentType contentType;

    /** 内容主键，创建前可为 null */
    private Long contentId;

    private Long userId;

    private String content;
}
