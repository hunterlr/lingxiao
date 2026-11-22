package com.lingxiao.modules.member.oauth;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序登录。
 *
 * <p>小程序没有网页授权地址：客户端先调用 {@code wx.login()} 拿到 code，
 * 再把它交给后端 {@code POST /api/app/auth/oauth/mini-login}。这里只实现
 * code 换 openid / unionid（jscode2session）；昵称与头像由小程序端自行补充，
 * 后端在没有昵称时使用平台名兜底，不伪造用户资料。</p>
 */
public class WeChatMiniOAuthProvider implements MemberOAuthProvider {

    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final MemberOAuthProviderSpec spec;
    private final OAuthHttpClient http;
    private final OAuthJson json;

    public WeChatMiniOAuthProvider(MemberOAuthProviderSpec spec, OAuthHttpClient http, OAuthJson json) {
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
        throw new OAuthException("wechat mini program uses mini-login, not browser authorize url");
    }

    @Override
    public OAuthUserInfo exchange(MemberOAuthProviderConfig config, String code, String codeVerifier) {
        String url = StringUtils.hasText(config.getTokenUrl()) ? config.getTokenUrl() : CODE2SESSION_URL;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appid", config.getClientId());
        params.put("secret", config.getClientSecret());
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");
        String body = http.get(json.buildUrl(url, params), null);
        Map<String, Object> result = json.parseObject(body);

        String errcode = json.string(result, "errcode");
        if (StringUtils.hasText(errcode) && !"0".equals(errcode)) {
            throw new OAuthException("wechat mini errcode: " + errcode + " " + json.string(result, "errmsg"));
        }
        String openId = json.string(result, "openid");
        if (!StringUtils.hasText(openId)) {
            throw new OAuthException("wechat mini did not return openid");
        }

        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(code());
        info.setOpenId(openId);
        info.setUnionId(json.string(result, "unionid"));
        info.setRawData(json.truncate(body));
        return info;
    }
}
