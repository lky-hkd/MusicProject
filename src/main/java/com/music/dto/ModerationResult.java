package com.music.dto;

import com.music.common.ModerationAction;
import com.music.common.ModerationStage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 一次内容审核的结果。
 */
@Data
@Builder
public class ModerationResult {

    private ModerationAction action;

    private ModerationStage stage;

    /** 词库命中的词（可能为空） */
    private List<WordHit> hits;

    /** 模型结论，未调用模型时为 null */
    private ModelVerdict verdict;

    /** 结论说明，用于日志/审计 */
    private String reason;

    private long latencyMs;
}
