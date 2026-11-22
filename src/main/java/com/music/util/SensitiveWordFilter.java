package com.music.util;

import com.music.common.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 敏感词过滤：启动时把词库加载进前缀树，发布动态/评论时校验。
 * 词库位置：classpath:sensitive-words.txt，一行一个词。
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    private final TrieNode root = new TrieNode();

    @PostConstruct
    public void init() {
        int count = 0;
        try (InputStream in = new ClassPathResource("sensitive-words.txt").getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    addWord(word);
                    count++;
                }
            }
            log.info("[post] 敏感词库加载完成，共 {} 个", count);
        } catch (Exception e) {
            log.warn("[post] 敏感词库加载失败：{}", e.getMessage());
        }
    }

    /** 命中则抛出业务异常，提示命中的词 */
    public void check(String text) {
        String hit = findFirst(text);
        if (hit != null) {
            throw new BizException("内容包含敏感词：" + hit);
        }
    }

    /** 返回第一个命中的敏感词，没有则返回 null */
    public String findFirst(String text) {
        if (text == null || text.isEmpty()) return null;
        for (int i = 0; i < text.length(); i++) {
            TrieNode node = root;
            for (int j = i; j < text.length(); j++) {
                node = node.children.get(text.charAt(j));
                if (node == null) break;
                if (node.end) return text.substring(i, j + 1);
            }
        }
        return null;
    }

    private void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.end = true;
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean end;
    }
}
