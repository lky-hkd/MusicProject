package com.music.util;

import com.music.common.BizException;
import com.music.dto.WordHit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词过滤（分级版）。
 *
 * 词库文件：classpath:sensitive-words.txt，一行一个词，支持用 TAB 指定级别：
 * <pre>
 *   赌博          // 未标级别，默认按高危处理（兼容旧词库）
 *   刷单    1     // 高危：直接拦截
 *   加微信  2     // 可疑：标记后交给模型复核
 *   傻逼    1 P   // 第三列 P 表示允许谐音拦截（这类词没有正常同音用法）
 * </pre>
 *
 * 除了精确匹配，还会对带 P 标记的词做音码（无声调拼音）匹配：
 * 「煞笔 / 沙比 / 撒币 / shabi」都能命中「傻逼」。没标 P 的词只做精确匹配，
 * 避免「读博 → 赌博」「扫货 → 骚货」这类同音误伤。
 *
 * 白名单文件：classpath:sensitive-words-whitelist.txt，一行一个短语，
 * 命中区间完全落在白名单短语内时忽略该次命中（用于避免「黄色潜水艇」这类误杀）。
 *
 * 匹配前会经过 {@link TextNormalizer}：全角转半角、去零宽字符、去空白与符号，
 * 因此「傻 逼」「s.b」这类拆字写法也能命中。
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    /** 高危：直接拦截 */
    public static final int TIER_BLOCK = 1;
    /** 可疑：交给模型复核 */
    public static final int TIER_REVIEW = 2;

    private static final String WORD_FILE = "sensitive-words.txt";
    private static final String WHITELIST_FILE = "sensitive-words-whitelist.txt";
    /** 音码最短长度，过短（如 sb）容易误伤正常英文缩写 */
    private static final int MIN_PINYIN_CODE = 4;

    private final TrieNode root = new TrieNode();
    private final List<String> whitelist = new ArrayList<>();
    /** 音码 → 命中模板（tier 已按「是否允许谐音拦截」确定） */
    private final Map<String, WordHit> pinyinIndex = new HashMap<>();
    private final TextNormalizer normalizer;
    private final PinyinCodec pinyinCodec;

    public SensitiveWordFilter(TextNormalizer normalizer, PinyinCodec pinyinCodec) {
        this.normalizer = normalizer;
        this.pinyinCodec = pinyinCodec;
    }

    @PostConstruct
    public void init() {
        int total = 0;
        int blockCount = 0;
        try (InputStream in = new ClassPathResource(WORD_FILE).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String content = line.trim();
                if (content.isEmpty() || content.startsWith("#")) {
                    continue;
                }
                int tier = parseAndAdd(content);
                total++;
                if (tier == TIER_BLOCK) {
                    blockCount++;
                }
            }
            log.info("[moderation] 敏感词库加载完成，共 {} 个（高危 {} / 可疑 {}），音码索引 {} 条",
                    total, blockCount, total - blockCount, pinyinIndex.size());
        } catch (Exception e) {
            log.warn("[moderation] 敏感词库加载失败：{}", e.getMessage());
        }

        loadWhitelist();
    }

    /**
     * 返回文本中命中的全部敏感词（已按级别标记）。
     */
    public List<WordHit> findAll(String raw) {
        String text = normalizer.normalizeForMatch(raw);
        if (text.isEmpty()) {
            return List.of();
        }
        List<int[]> protectedRanges = whitelistRanges(text);
        Map<String, WordHit> hits = new LinkedHashMap<>();
        // 被白名单放行的词，音码阶段也不能再加回来
        Set<String> protectedWords = new HashSet<>();
        for (int i = 0; i < text.length(); i++) {
            TrieNode node = root;
            for (int j = i; j < text.length(); j++) {
                node = node.children.get(text.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.tier > 0) {
                    String word = text.substring(i, j + 1);
                    if (isProtected(i, j, protectedRanges)) {
                        protectedWords.add(word);
                    } else {
                        hits.putIfAbsent(word, new WordHit(word, node.tier, i));
                    }
                }
            }
        }
        collectHomophones(text, hits, protectedWords);
        return new ArrayList<>(hits.values());
    }

    /**
     * 音码匹配：抓「煞笔 / 沙比 / 撒币 / shabi」这类谐音写法。
     * 只有词库里标了 P 的词才参与，且沿用原级别；普通词只做精确匹配，
     * 避免「扫货 → 骚货」「司马 → 死妈」这类同音误伤。
     */
    private void collectHomophones(String text, Map<String, WordHit> hits, Set<String> protectedWords) {
        if (pinyinIndex.isEmpty()) {
            return;
        }
        String code = pinyinCodec.toCode(text);
        if (code.length() < MIN_PINYIN_CODE) {
            return;
        }
        for (Map.Entry<String, WordHit> entry : pinyinIndex.entrySet()) {
            WordHit template = entry.getValue();
            // 原文已经精确命中或被白名单放行，不再重复记一次谐音
            if (protectedWords.contains(template.getWord()) || hits.containsKey(template.getWord())) {
                continue;
            }
            int idx = code.indexOf(entry.getKey());
            if (idx < 0) {
                continue;
            }
            String display = template.getWord() + "(谐音)";
            hits.putIfAbsent(display, new WordHit(display, template.getTier(), idx));
        }
    }

    /**
     * 高危词直接抛业务异常；可疑词只记录日志，交给后续模型复核。
     * 对外提示不返回命中的具体词，避免词库被逐条试探反推。
     */
    public void check(String text) {
        List<WordHit> hits = findAll(text);
        if (hits.isEmpty()) {
            return;
        }
        WordHit blockHit = hits.stream()
                .filter(h -> h.getTier() == TIER_BLOCK)
                .findFirst()
                .orElse(null);
        if (blockHit != null) {
            log.warn("[moderation] 高危词命中 word={} length={}", blockHit.getWord(), text == null ? 0 : text.length());
            throw new BizException("内容包含违规信息，请修改后重试");
        }
        log.info("[moderation] 命中可疑词，待模型复核 hits={} length={}",
                hits, text == null ? 0 : text.length());
    }

    // ======================= 词库加载 =======================

    /** 解析一行词条并写入前缀树，返回该词的级别 */
    private int parseAndAdd(String content) {
        // 支持：词 / 词<TAB>级别 / 词<TAB>级别<TAB>P（P 表示允许谐音拦截）
        String[] parts = content.split("[\\t\\s]+");
        String word = parts[0].trim();
        int tier = parts.length > 1 ? parseTier(parts[1]) : TIER_BLOCK;   // 兼容旧词库：未标级别默认按高危处理
        boolean homophoneBlock = parts.length > 2 && "P".equalsIgnoreCase(parts[2]);
        if (word.isEmpty()) {
            return tier;
        }
        addWord(word, tier, homophoneBlock);
        return tier;
    }

    private int parseTier(String raw) {
        try {
            int tier = Integer.parseInt(raw.trim());
            return (tier == TIER_REVIEW) ? TIER_REVIEW : TIER_BLOCK;
        } catch (Exception e) {
            return TIER_BLOCK;
        }
    }

    private void addWord(String word, int tier, boolean homophoneBlock) {
        String normalized = normalizer.normalizeForMatch(word);
        if (normalized.isEmpty()) {
            return;
        }
        TrieNode node = root;
        for (char c : normalized.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        // 同一个词配置了多个级别时，取更严格的级别
        node.tier = (node.tier == 0) ? tier : Math.min(node.tier, tier);

        // 只有标了 P 的词参与音码匹配：这些词没有正常同音用法，误伤可控
        if (homophoneBlock) {
            String code = pinyinCodec.toCode(word);
            if (code.length() >= MIN_PINYIN_CODE) {
                WordHit exist = pinyinIndex.get(code);
                if (exist == null || tier < exist.getTier()) {
                    pinyinIndex.put(code, new WordHit(word, tier, 0));
                }
            }
        }
    }

    private void loadWhitelist() {
        try {
            ClassPathResource resource = new ClassPathResource(WHITELIST_FILE);
            if (!resource.exists()) {
                return;
            }
            try (InputStream in = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String content = line.trim();
                    if (content.isEmpty() || content.startsWith("#")) {
                        continue;
                    }
                    String normalized = normalizer.normalizeForMatch(content);
                    if (!normalized.isEmpty()) {
                        whitelist.add(normalized);
                    }
                }
            }
            log.info("[moderation] 白名单加载完成，共 {} 条", whitelist.size());
        } catch (Exception e) {
            log.warn("[moderation] 白名单加载失败：{}", e.getMessage());
        }
    }

    // ======================= 白名单区间 =======================

    private List<int[]> whitelistRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        if (whitelist.isEmpty()) {
            return ranges;
        }
        for (String term : whitelist) {
            int from = 0;
            while (true) {
                int idx = text.indexOf(term, from);
                if (idx < 0) {
                    break;
                }
                ranges.add(new int[]{idx, idx + term.length() - 1});
                from = idx + 1;
            }
        }
        return ranges;
    }

    /** 命中区间完全落在某个白名单短语内时忽略 */
    private boolean isProtected(int start, int end, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (start >= range[0] && end <= range[1]) {
                return true;
            }
        }
        return false;
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        /** 0 表示不是词尾；否则为级别 */
        private int tier;
    }
}
