package com.music.common;

/**
 * 审核结论由哪一层给出。
 */
public enum ModerationStage {
    /** 词库规则 */
    RULE,
    /** 模型复核 */
    MODEL,
    /** 模型不可用/超时后的降级策略 */
    FALLBACK
}
