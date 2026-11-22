package com.lingxiao.modules.member.oauth;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标准 OAuth2 / OIDC Provider。
 *
 * <p>通过 {@link MemberOAuthProviderSpec} 描述差异，覆盖授权码换 Token、
 * Bearer / Query / Header 三种用户信息认证方式，以及嵌套字段路径。
 * 不标准的平台（微信、QQ、支付宝、Apple）由独立实现覆盖。</p>
 */
public class StandardOAuthProvider implements MemberOAuthProvider {

    protected final MemberOAuthProviderSpec spec;
    protected final OAuthHttpClient http;
    protected final OAuthJson json;

    public StandardOAuthProvider(MemberOAuthProviderSpec spec, OAuthHttpClient http, OAuthJson json) {
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
        String base = config.getAuthorizeUrl();
        if (!isHttpUrl(base)) {
            throw new OAuthException("invalid authorize url for " + code());
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put(spec.getClientIdParam(), config.getClientId());
        params.put(spec.getRedirectParam(), config.getRedirectUri());
        params.put(spec.getResponseTypeParam(), "code");
        if (StringUtils.hasText(config.getScope())) {
            params.put(spec.getScopeParam(), config.getScope());
        }
        params.put(spec.getStateParam(), state);
        params.putAll(spec.getAuthorizeParams());
        if (spec.isPkce() && StringUtils.hasText(codeChallenge)) {
            params.put("code_challenge", codeChallenge);
            params.put("code_challenge_method", "S256");
        }
        return json.buildUrl(base, params);
    }

    @Override
    public OAuthUserInfo exchange(MemberOAuthProviderConfig config, String code, String codeVerifier) {
        String tokenUrl = config.getTokenUrl();
        if (!isHttpUrl(tokenUrl)) {
            throw new OAuthException("invalid token url for " + code());
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put(spec.getTokenGrantTypeParam(), spec.getTokenGrantTypeValue());
        form.put(spec.getTokenCodeParam(), code);
        if (spec.isTokenWithRedirect()) {
            form.put(spec.getRedirectParam(), config.getRedirectUri());
        }
        form.put(spec.getTokenClientIdParam(), config.getClientId());
        Map<String, String> headers = new LinkedHashMap<>(spec.getTokenHeaders());
        if (spec.isTokenBasicAuth()) {
            String raw = config.getClientId() + ":" + nullSafe(config.getClientSecret());
            headers.put("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        } else {
            form.put(spec.getTokenClientSecretParam(), config.getClientSecret());
        }
        if (spec.isPkce() && StringUtils.hasText(codeVerifier)) {
            form.put("code_verifier", codeVerifier);
        }
        form.putAll(spec.getTokenParams());

        String tokenBody = spec.isTokenJson()
                ? http.postJson(tokenUrl, new LinkedHashMap<>(form), headers)
                : http.postForm(tokenUrl, form, headers);
        Map<String, Object> token = json.parseObject(tokenBody);
        checkError(token);

        String accessToken = json.string(token, spec.getTokenAccessTokenPath());
        if (!StringUtils.hasText(accessToken)) {
            throw new OAuthException("no access token returned by " + code());
        }
        String tokenOpenId = spec.getTokenOpenIdPath() == null
                ? null : json.string(token, spec.getTokenOpenIdPath());
        String tokenUnionId = spec.getTokenUnionIdPath() == null
                ? null : json.string(token, spec.getTokenUnionIdPath());

        String userInfoUrl = config.getUserInfoUrl();
        if (!isHttpUrl(userInfoUrl)) {
            throw new OAuthException("invalid user info url for " + code());
        }
        if (StringUtils.hasText(spec.getUserInfoQuery())) {
            String query = spec.getUserInfoQuery()
                    .replace("{openId}", tokenOpenId == null ? "" : json.encode(tokenOpenId))
                    .replace("{accessToken}", json.encode(accessToken));
            userInfoUrl = userInfoUrl + (userInfoUrl.contains("?") ? "&" : "?") + query;
        }
        Map<String, String> userHeaders = new LinkedHashMap<>(spec.getUserInfoHeaders());
        if ("bearer".equalsIgnoreCase(spec.getUserInfoAuth())) {
            userHeaders.put("Authorization", "Bearer " + accessToken);
        } else if ("query".equalsIgnoreCase(spec.getUserInfoAuth())) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("access_token", accessToken);
            userInfoUrl = json.appendQuery(userInfoUrl, query);
        } else if ("header".equalsIgnoreCase(spec.getUserInfoAuth())) {
            userHeaders.put(spec.getUserInfoHeaderName(), accessToken);
        }
        if (StringUtils.hasText(spec.getClientIdHeaderName())) {
            userHeaders.put(spec.getClientIdHeaderName(), config.getClientId());
        }

        String userBody = http.get(userInfoUrl, userHeaders);
        Map<String, Object> user = json.parseObject(userBody);
        checkError(user);

        String openId = firstText(json.string(user, spec.getIdPath()), tokenOpenId);
        if (!StringUtils.hasText(openId)) {
            throw new OAuthException("no open id returned by " + code());
        }
        String unionId = firstText(json.string(user, spec.getUnionIdPath()), tokenUnionId);
        String nickname = firstText(json.string(user, spec.getNicknamePath()), displayName() + "用户");
        String avatar = json.string(user, spec.getAvatarPath());
        if (StringUtils.hasText(spec.getAvatarUrlTemplate()) && StringUtils.hasText(avatar)) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("id", openId);
            values.put("avatar", avatar);
            values.put("nickname", nickname);
            avatar = json.template(spec.getAvatarUrlTemplate(), values);
        }
        if (!isHttpUrl(avatar)) {
            avatar = null;
        } else {
            avatar = avatar.replace("&amp;", "&");
        }

        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(code());
        info.setOpenId(openId);
        info.setUnionId(unionId);
        info.setNickname(nickname);
        info.setAvatar(avatar);
        info.setEmail(json.string(user, spec.getEmailPath()));
        info.setRawData(json.truncate(userBody));
        return info;
    }

    /** 第三方平台常见的错误字段：error / errcode / error_code。 */
    protected void checkError(Map<String, Object> body) {
        String error = json.string(body, "error");
        if (StringUtils.hasText(error) && !"0".equals(error) && !"false".equalsIgnoreCase(error)) {
            throw new OAuthException("provider error: " + error + " " + nullSafe(json.string(body, "error_description")));
        }
        String errcode = json.string(body, "errcode");
        if (StringUtils.hasText(errcode) && !"0".equals(errcode)) {
            throw new OAuthException("provider errcode: " + errcode + " " + nullSafe(json.string(body, "errmsg")));
        }
        String errorCode = json.string(body, "error_code");
        if (StringUtils.hasText(errorCode) && !"0".equals(errorCode)) {
            throw new OAuthException("provider error_code: " + errorCode + " " + nullSafe(json.string(body, "description")));
        }
    }

    protected boolean isHttpUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String lower = url.trim().toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    protected String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    protected String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
