package com.music.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.music.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class LlmUtil {

    @Value("${llm.api-key}")
    private String API_KEY;

    @Value("${llm.endpoint}")
    private String API_URL;

    @Value("${llm.model-id}")
    private String MODEL_NAME;

    /**
     * 提取搜索关键词，只返回简短关键词，不要多余文字
     */
    public String extractKeyword(String userQuery) throws Exception {
        String sysPrompt = "你是关键词提取器，从用户输入的歌词/描述中提取核心搜索关键词，只输出关键词，不要任何解释、多余文字。";
        String body = buildRequestBody(sysPrompt, userQuery);
        String resp = httpPost(body);
        // 解析大模型返回内容
        try {
            JSONObject json = JSON.parseObject(resp);
            String keyword = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            return cleanKeyword(keyword);
        } catch (Exception e) {
            log.error("解析大模型返回失败:{}", resp, e);
            throw new BizException("AI 搜索服务暂时不可用");
        }
    }

    private String cleanKeyword(String keyword) {
        if (keyword == null) return "";
        String k = keyword.replaceAll("[\"'“”‘’\\r\\n]", "").trim();
        if (k.length() > 50) k = k.substring(0, 50);
        return k;
    }


    // 抽离公共的构建请求体
    private String buildRequestBody(String systemContent, String userContent) {
        JSONObject reqBody = new JSONObject();
        reqBody.put("model", MODEL_NAME);
        JSONArray messages = new JSONArray();

        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemContent);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        reqBody.put("messages", messages);
        reqBody.put("temperature", 0.2);
        return reqBody.toJSONString();
    }

    // 公共POST请求
    private String httpPost(String jsonBody) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            java.io.InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            conn.disconnect();   // 无论成功失败都释放
        }
    }
}
