package com.music.common;

/**
 * 被审核内容的类型，用于审计与异步命中后的下架。
 */
public enum ModerationContentType {
    POST,
    COMMENT,
    REVIEW,
    NICKNAME,
    SIGNATURE,
    /** 私聊消息 */
    CHAT,
    /** 无具体内容的纯文本（如商家回复） */
    TEXT
}
