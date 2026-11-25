package com.music.service;

import com.music.dto.ModelVerdict;

/**
 * 内容审核模型客户端。
 * 实现可以是本地 Ollama / vLLM 的 OpenAI 兼容接口，也可以是云端模型。
 */
public interface ModerationModelClient {

    /**
     * 复核一段文本。
     *
     * @return 模型结论；模型未启用、不可用或超时返回 null（由调用方走降级策略）
     */
    ModelVerdict judge(String content);

    /** 启动预热：让模型提前加载进显存，默认空实现 */
    default void warmup() {
    }
}
