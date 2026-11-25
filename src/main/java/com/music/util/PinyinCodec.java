package com.music.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 音码：把汉字转成无声调拼音，用于谐音匹配。
 *
 * 「煞笔 / 沙比 / 撒币 / shabi」转出来都是 sabi，因此不需要穷举变体。
 * 平翘舌统一（zh→z、ch→c、sh→s），覆盖「傻逼 shabi」与「撒币 sabi」这类口音差异。
 *
 * 注意：多音字只取第一个读音，音码匹配一律按可疑处理，避免「读博 → 赌博」这种误拦。
 */
@Component
public class PinyinCodec {

    private final HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();

    public PinyinCodec() {
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    /**
     * 转音码：汉字转拼音，非汉字只保留字母数字，最后做平翘舌归一。
     * 这样直接输入拼音（如 shabi）也能参与匹配。
     */
    public String toCode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                try {
                    String[] arr = PinyinHelper.toHanyuPinyinStringArray((char) cp, format);
                    if (arr != null && arr.length > 0) {
                        sb.append(arr[0]);
                        continue;
                    }
                } catch (Exception ignored) {
                    // 取不到读音就按普通字符处理
                }
            }
            if (Character.isLetterOrDigit(cp)) {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT)
                .replace("zh", "z")
                .replace("ch", "c")
                .replace("sh", "s");
    }
}
