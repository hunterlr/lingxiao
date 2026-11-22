package com.lingxiao.modules.member.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第三方登录的 JSON / URL 工具。
 *
 * <p>各家用户信息字段层级不同（{@code data.open_id}、{@code images[0].url}、
 * {@code response.nickname}），统一用点路径读取，避免每个 Provider 写一套 Map 断言。</p>
 */
@Component
@RequiredArgsConstructor
public class OAuthJson {

    private static final int RAW_MAX_LENGTH = 4000;

    private final ObjectMapper objectMapper;

    public Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            throw new OAuthException("empty response");
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new OAuthException("invalid JSON response", e);
        }
    }

    @SuppressWarnings("unchecked")
    public String string(Map<String, Object> root, String path) {
        Object value = read(root, path);
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public Object read(Map<String, Object> root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return null;
        }
        Object current = root;
        for (String token : path.split("\\.")) {
            current = readToken(current, token);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Object readToken(Object current, String token) {
        if (current == null || !StringUtils.hasText(token)) {
            return current;
        }
        int bracket = token.indexOf('[');
        String key = bracket >= 0 ? token.substring(0, bracket) : token;
        if (!key.isEmpty()) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(key);
        }
        while (bracket >= 0 && current != null) {
            int end = token.indexOf(']', bracket);
            if (end < 0) {
                return null;
            }
            int index;
            try {
                index = Integer.parseInt(token.substring(bracket + 1, end));
            } catch (NumberFormatException e) {
                return null;
            }
            if (!(current instanceof List)) {
                return null;
            }
            List<Object> list = (List<Object>) current;
            current = index >= 0 && index < list.size() ? list.get(index) : null;
            bracket = token.indexOf('[', end + 1);
        }
        return current;
    }

    /** 用 {id} / {avatar} 等占位符拼接头像地址。 */
    public String template(String template, Map<String, String> values) {
        if (!StringUtils.hasText(template)) {
            return null;
        }
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new OAuthException("UTF-8 not supported", e);
        }
    }

    /** 在已有 query 的 URL 后追加参数，自动处理 ? / &。 */
    public String appendQuery(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        char separator = url.contains("?") ? '&' : '?';
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            builder.append(separator)
                    .append(encode(entry.getKey()))
                    .append('=')
                    .append(encode(entry.getValue()));
            separator = '&';
        }
        return builder.toString();
    }

    public String buildUrl(String base, Map<String, String> params) {
        Map<String, String> ordered = new LinkedHashMap<>();
        if (params != null) {
            ordered.putAll(params);
        }
        return appendQuery(base, ordered);
    }

    public String truncate(String value) {
        if (value == null || value.length() <= RAW_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, RAW_MAX_LENGTH);
    }
}
