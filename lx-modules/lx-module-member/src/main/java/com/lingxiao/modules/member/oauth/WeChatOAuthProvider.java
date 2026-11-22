package com.lingxiao.modules.member.oauth;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信登录（开放平台网站应用 / 公众号网页授权）。
 *
 * <p>微信的授权、换 Token 与用户信息都不是标准 OAuth2：字段名是
 * {@code appid / secret / openid}，错误放在 {@code errcode}，用户信息还要再请求一次。
 * 两个场景只有授权 scope 与入口不同，因此共用一个实现。</p>
 */
public class WeChatOAuthProvider implements MemberOAuthProvider {

    private static final String AUTHORIZE_OPEN = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String AUTHORIZE_MP = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String USER_INFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    private final MemberOAuthProviderSpec spec;
    private final OAuthHttpClient http;
    private final OAuthJson json;
    private final boolean mp;

    public WeChatOAuthProvider(MemberOAuthProviderSpec spec, OAuthHttpClient http, OAuthJson json, boolean mp) {
        this.spec = spec;
        this.http = http;
        this.json = json;
        this.mp = mp;
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
        String base = StringUtils.hasText(config.getAuthorizeUrl())
                ? config.getAuthorizeUrl()
                : (mp ? AUTHORIZE_MP : AUTHORIZE_OPEN);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appid", config.getClientId());
        params.put("redirect_uri", config.getRedirectUri());
        params.put("response_type", "code");
        params.put("scope", StringUtils.hasText(config.getScope()) ? config.getScope() : spec.getDefaultScope());
        params.put("state", state);
        String url = json.buildUrl(base, params);
        // 微信要求授权地址必须带这个 fragment
        return url + "#wechat_redirect";
    }

    @Override
    public OAuthUserInfo exchange(MemberOAuthProviderConfig config, String code, String codeVerifier) {
        String tokenUrl = StringUtils.hasText(config.getTokenUrl()) ? config.getTokenUrl() : TOKEN_URL;
        Map<String, String> tokenParams = new LinkedHashMap<>();
        tokenParams.put("appid", config.getClientId());
        tokenParams.put("secret", config.getClientSecret());
        tokenParams.put("code", code);
        tokenParams.put("grant_type", "authorization_code");
        Map<String, Object> token = json.parseObject(http.get(json.buildUrl(tokenUrl, tokenParams), null));
        checkError(token);

        String accessToken = json.string(token, "access_token");
        String openId = json.string(token, "openid");
        String unionId = json.string(token, "unionid");
        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(openId)) {
            throw new OAuthException("wechat did not return access_token / openid");
        }

        String userInfoUrl = StringUtils.hasText(config.getUserInfoUrl()) ? config.getUserInfoUrl() : USER_INFO_URL;
        Map<String, String> userParams = new LinkedHashMap<>();
        userParams.put("access_token", accessToken);
        userParams.put("openid", openId);
        userParams.put("lang", "zh_CN");
        String userBody = http.get(json.buildUrl(userInfoUrl, userParams), null);
        Map<String, Object> user = json.parseObject(userBody);
        checkError(user);

        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(code());
        info.setOpenId(first(json.string(user, "openid"), openId));
        info.setUnionId(first(json.string(user, "unionid"), unionId));
        info.setNickname(first(json.string(user, "nickname"), displayName() + "用户"));
        String avatar = json.string(user, "headimgurl");
        info.setAvatar(isHttpUrl(avatar) ? avatar : null);
        info.setRawData(json.truncate(userBody));
        return info;
    }

    private void checkError(Map<String, Object> body) {
        String errcode = json.string(body, "errcode");
        if (StringUtils.hasText(errcode) && !"0".equals(errcode)) {
            throw new OAuthException("wechat errcode: " + errcode + " " + json.string(body, "errmsg"));
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
