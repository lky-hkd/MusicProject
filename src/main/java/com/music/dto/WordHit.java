package com.music.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 敏感词命中结果。
 */
@Data
@AllArgsConstructor
public class WordHit {

    /** 命中的词（规范化之后的文本片段） */
    private String word;

    /** 级别：1=高危（直接拦截） 2=可疑（交给模型复核） */
    private int tier;

    /** 在规范化文本中的起始下标 */
    private int index;
}
