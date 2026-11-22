package com.lingxiao.modules.member.oauth;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * QQ 互联登录。
 *
 * <p>QQ 的流程比标准 OAuth2 多一步：先用 access_token 调 {@code /oauth2.0/me}
 * 拿 openid，再带 openid 取用户信息。Token 接口还可能返回
 * {@code access_token=...&expires_in=...} 而不是 JSON，这里两种都兼容。</p>
 */
public class QqOAuthProvider implements MemberOAuthProvider {

    private static final String AUTHORIZE_URL = "https://graph.qq.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://graph.qq.com/oauth2.0/token";
    private static final String ME_URL = "https://graph.qq.com/oauth2.0/me";
    private static final String USER_INFO_URL = "https://graph.qq.com/user/get_user_info";

    private final MemberOAuthProviderSpec spec;
    private final OAuthHttpClient http;
    private final OAuthJson json;

    public QqOAuthProvider(MemberOAuthProviderSpec spec, OAuthHttpClient http, OAuthJson json) {
        this.spec = spec;
        this.http = http;
        this.json = json;
    }

    @Override
    public String code() {
        return spec.getCode();
    }

    @Override
    public String displayName() {
        return spec.getDisplayName();
    }

    @Override
    public String group() {
        return spec.getGroup();
    }

    @Override
    public String authorizeUrl(MemberOAuthProviderConfig config, String state, String codeChallenge) {
        String base = StringUtils.hasText(config.getAuthorizeUrl()) ? config.getAuthorizeUrl() : AUTHORIZE_URL;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", config.getClientId());
        params.put("redirect_uri", config.getRedirectUri());
        params.put("state", state);
        params.put("scope", StringUtils.hasText(config.getScope()) ? config.getScope() : "get_user_info");
        return json.buildUrl(base, params);
    }

    @Override
    public OAuthUserInfo exchange(MemberOAuthProviderConfig config, String code, String codeVerifier) {
        String tokenUrl = StringUtils.hasText(config.getTokenUrl()) ? config.getTokenUrl() : TOKEN_URL;
        Map<String, String> tokenParams = new LinkedHashMap<>();
        tokenParams.put("grant_type", "authorization_code");
        tokenParams.put("client_id", config.getClientId());
        tokenParams.put("client_secret", config.getClientSecret());
        tokenParams.put("code", code);
        tokenParams.put("redirect_uri", config.getRedirectUri());
        tokenParams.put("fmt", "json");
        String tokenBody = http.get(json.buildUrl(tokenUrl, tokenParams), null);
        Map<String, Object> token = parseFlexible(tokenBody);
        checkError(token);
        String accessToken = json.string(token, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new OAuthException("QQ did not return access_token");
        }

        Map<String, String> meParams = new LinkedHashMap<>();
        meParams.put("access_token", accessToken);
        meParams.put("fmt", "json");
        String meBody = http.get(json.buildUrl(ME_URL, meParams), null);
        Map<String, Object> me = parseFlexible(stripCallback(meBody));
        checkError(me);
        String openId = json.string(me, "openid");
        if (!StringUtils.hasText(openId)) {
            throw new OAuthException("QQ did not return openid");
        }

        String userInfoUrl = StringUtils.hasText(config.getUserInfoUrl()) ? config.getUserInfoUrl() : USER_INFO_URL;
        Map<String, String> userParams = new LinkedHashMap<>();
        userParams.put("access_token", accessToken);
        userParams.put("oauth_consumer_key", config.getClientId());
        userParams.put("openid", openId);
        userParams.put("fmt", "json");
        String userBody = http.get(json.buildUrl(userInfoUrl, userParams), null);
        Map<String, Object> user = json.parseObject(userBody);
        checkError(user);
        String ret = json.string(user, "ret");
        if (StringUtils.hasText(ret) && !"0".equals(ret)) {
            throw new OAuthException("QQ user info ret: " + ret + " " + json.string(user, "msg"));
        }

        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(code());
        info.setOpenId(openId);
        info.setNickname(first(json.string(user, "nickname"), displayName() + "用户"));
        String avatar = first(json.string(user, "figureurl_qq_2"),
                first(json.string(user, "figureurl_qq_1"),
                        first(json.string(user, "figureurl_2"), json.string(user, "figureurl_1"))));
        info.setAvatar(isHttpUrl(avatar) ? avatar : null);
        info.setRawData(json.truncate(userBody));
        return info;
    }

    /** QQ 的 token 接口可能是 JSON，也可能是 a=b&amp;c=d。 */
    private Map<String, Object> parseFlexible(String body) {
        if (!StringUtils.hasText(body)) {
            throw new OAuthException("QQ returned empty response");
        }
        String text = body.trim();
        if (text.startsWith("{")) {
            return json.parseObject(text);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String pair : text.split("&")) {
            int index = pair.indexOf('=');
            if (index > 0) {
                result.put(pair.substring(0, index), pair.substring(index + 1));
            }
        }
        return result;
    }

    /** /me 返回 callback( {...} );，去掉外层包裹再解析。 */
    private String stripCallback(String body) {
        String text = body == null ? "" : body.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private void checkError(Map<String, Object> body) {
        String error = json.string(body, "error");
        if (StringUtils.hasText(error) && !"0".equals(error)) {
            throw new OAuthException("QQ error: " + error + " " + json.string(body, "error_description"));
        }
    }

    private String first(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean isHttpUrl(String url) {
        return StringUtils.hasText(url)
                && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
