package com.music.dto;

import lombok.Data;

import java.util.List;

/**
 * 模型返回的审核结论（内部使用）。
 */
@Data
public class ModelVerdict {

    /** allow / review / block */
    private String decision;

    /** 0~1 */
    private double confidence;

    /** 违规类别，如 insult / ad / porn / illegal / politics */
    private List<String> categories;

    private String reason;
}
