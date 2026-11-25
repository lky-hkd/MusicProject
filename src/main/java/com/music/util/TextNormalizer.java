package com.music.util;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 文本规范化：在进入敏感词匹配前做统一清洗，降低「拆字/全角/零宽」等绕过手段的成功率。
 *
 * 后续可在这里继续扩展：
 *  - 繁简转换（引入映射表）
 *  - 拼音 / 谐音映射
 *  - 常见变体词替换
 */
@Component
public class TextNormalizer {

    /**
     * 规范化文本：NFKC（全角转半角、兼容字符归一）→ 去掉零宽/控制字符 → 小写。
     * 保留空白和标点，适合展示或送入模型。
     */
    public String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            // 零宽字符(FORMAT)、控制字符、孤立代理项直接丢弃
            if (type == Character.FORMAT || type == Character.CONTROL || type == Character.SURROGATE) {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * 压缩文本：只保留字母、数字、汉字，去掉空白与所有符号。
     * 用于敏感词匹配，可覆盖「傻 逼」「s.b」「加-微-信」这类拆字绕过。
     */
    public String compact(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    /** 规范化 + 压缩，敏感词匹配统一使用这个入口 */
    public String normalizeForMatch(String raw) {
        return compact(normalize(raw));
    }
}
