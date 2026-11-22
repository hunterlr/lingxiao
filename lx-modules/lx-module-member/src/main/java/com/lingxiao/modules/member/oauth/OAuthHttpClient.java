package com.lingxiao.modules.member.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 第三方登录 HTTP 客户端。
 *
 * <p>统一超时与错误处理：第三方平台超时 / 4xx / 5xx 都转成
 * {@link OAuthException}，由会员服务记录日志并给门户稳定错误码，
 * 不把上游 HTML 错误页或密钥拼进响应。</p>
 */
@Slf4j
@Component
public class OAuthHttpClient {

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;
    private static final int ERROR_BODY_MAX = 400;
    private static final String USER_AGENT = "LingXiao-Member-OAuth/1.0";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OAuthHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
    }

    public String get(String url, Map<String, String> headers) {
        return exchange(url, HttpMethod.GET, new HttpEntity<>(headers(headers, null)));
    }

    public String postForm(String url, Map<String, String> form, Map<String, String> headers) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        if (form != null) {
            form.forEach(body::add);
        }
        return exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers(headers, MediaType.APPLICATION_FORM_URLENCODED)));
    }

    public String postJson(String url, Map<String, Object> body, Map<String, String> headers) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new OAuthException("cannot serialize token request", e);
        }
        return exchange(url, HttpMethod.POST,
                new HttpEntity<>(json, headers(headers, MediaType.APPLICATION_JSON)));
    }

    private String exchange(String url, HttpMethod method, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            log.warn("第三方接口返回错误: status={}, url={}, body={}",
                    e.getRawStatusCode(), safeUrl(url), abbreviate(body));
            throw new OAuthException("provider returned HTTP " + e.getRawStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("第三方接口网络异常: url={}, error={}", safeUrl(url), e.getMessage());
            throw new OAuthException("provider network error", e);
        } catch (RuntimeException e) {
            log.warn("第三方接口调用失败: url={}, error={}", safeUrl(url), e.getMessage());
            throw new OAuthException("provider request failed", e);
        }
    }

    private HttpHeaders headers(Map<String, String> custom, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        if (custom != null) {
            custom.forEach((key, value) -> {
                if (key != null && value != null) {
                    headers.set(key, value);
                }
            });
        }
        return headers;
    }

    /** 日志里去掉 query，避免 access_token / code 落盘。 */
    private String safeUrl(String url) {
        if (url == null) {
            return null;
        }
        int index = url.indexOf('?');
        return index < 0 ? url : url.substring(0, index) + "?<redacted>";
    }

    private String abbreviate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= ERROR_BODY_MAX ? body : body.substring(0, ERROR_BODY_MAX) + "...";
    }
}
