package com.music.common;

/**
 * 内容审核最终动作。
 */
public enum ModerationAction {
    /** 放行 */
    ALLOW,
    /** 可疑：待模型确认或转人工 */
    REVIEW,
    /** 拦截 */
    BLOCK
}
