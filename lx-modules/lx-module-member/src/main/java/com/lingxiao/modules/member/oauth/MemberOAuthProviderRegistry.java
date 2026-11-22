package com.lingxiao.modules.member.oauth;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第三方登录 Provider 注册表。
 *
 * <p>“已实现”与“已启用”是两件事：这里列出所有可用适配器与内置端点，
 * 后台再按 {@code mem_oauth_provider} 配置启停与密钥。没有实现的平台不放进注册表，
 * 避免参数页出现“点了没反应”的开关。</p>
 *
 * <p>国内：微信开放平台 / 微信公众号 / QQ / 微博 / 支付宝 / 抖音 / 钉钉 / Gitee；
 * 国外：GitHub / GitLab / Google / Microsoft / Apple / Facebook / X / LinkedIn /
 * Discord / LINE / Kakao / Naver / Amazon / Yahoo / Reddit / Slack / Twitch / Spotify。</p>
 */
@Component
public class MemberOAuthProviderRegistry {

    public static final String GROUP_CN = "cn";
    public static final String GROUP_GLOBAL = "global";

    private final Map<String, MemberOAuthProvider> providers;
    private final Map<String, MemberOAuthProviderSpec> specs;

    public MemberOAuthProviderRegistry(OAuthHttpClient http, OAuthJson json) {
        List<MemberOAuthProviderSpec> list = buildSpecs();
        Map<String, MemberOAuthProvider> providerMap = new LinkedHashMap<>();
        Map<String, MemberOAuthProviderSpec> specMap = new LinkedHashMap<>();
        for (MemberOAuthProviderSpec spec : list) {
            specMap.put(spec.getCode(), spec);
            providerMap.put(spec.getCode(), create(spec, http, json));
        }
        this.providers = Collections.unmodifiableMap(providerMap);
        this.specs = Collections.unmodifiableMap(specMap);
    }

    public MemberOAuthProvider get(String provider) {
        return providers.get(provider);
    }

    public MemberOAuthProviderSpec spec(String provider) {
        return specs.get(provider);
    }

    public List<MemberOAuthProviderSpec> specs() {
        return new ArrayList<>(specs.values());
    }

    private MemberOAuthProvider create(MemberOAuthProviderSpec spec, OAuthHttpClient http, OAuthJson json) {
        if ("wechat_open".equals(spec.getCode())) {
            return new WeChatOAuthProvider(spec, http, json, false);
        }
        if ("wechat_mp".equals(spec.getCode())) {
            return new WeChatOAuthProvider(spec, http, json, true);
        }
        if ("wechat_mini".equals(spec.getCode())) {
            return new WeChatMiniOAuthProvider(spec, http, json);
        }
        if ("qq".equals(spec.getCode())) {
            return new QqOAuthProvider(spec, http, json);
        }
        if ("alipay".equals(spec.getCode())) {
            return new AlipayOAuthProvider(spec, http, json);
        }
        if ("apple".equals(spec.getCode())) {
            return new AppleOAuthProvider(spec, http, json);
        }
        return new StandardOAuthProvider(spec, http, json);
    }

    private List<MemberOAuthProviderSpec> buildSpecs() {
        List<MemberOAuthProviderSpec> list = new ArrayList<>();

        // ==================== 国内 ====================
        list.add(spec("wechat_open", "微信开放平台", GROUP_CN,
                "https://open.weixin.qq.com/connect/qrconnect", "", "", "snsapi_login", 10));
        list.add(spec("wechat_mp", "微信公众号", GROUP_CN,
                "https://open.weixin.qq.com/connect/oauth2/authorize", "", "", "snsapi_userinfo", 20));
        list.add(MemberOAuthProviderSpec.builder()
                .code("wechat_mini").displayName("微信小程序").group(GROUP_CN)
                .tokenUrl("https://api.weixin.qq.com/sns/jscode2session")
                .defaultScope("")
                .browser(false)
                .build());
        list.add(spec("qq", "QQ", GROUP_CN,
                "https://graph.qq.com/oauth2.0/authorize", "", "", "get_user_info", 30));
        list.add(MemberOAuthProviderSpec.builder()
                .code("weibo").displayName("新浪微博").group(GROUP_CN)
                .authorizeUrl("https://api.weibo.com/oauth2/authorize")
                .tokenUrl("https://api.weibo.com/oauth2/access_token")
                .userInfoUrl("https://api.weibo.com/2/users/show.json")
                .defaultScope("")
                .tokenOpenIdPath("uid")
                .userInfoAuth("query")
                .userInfoQuery("uid={openId}")
                .idPath("id").nicknamePath("screen_name").avatarPath("profile_image_url")
                .build());
        list.add(spec("alipay", "支付宝", GROUP_CN,
                "https://openauth.alipay.com/oauth2/publicAppAuthorize.htm", "", "", "auth_user", 50));
        list.add(MemberOAuthProviderSpec.builder()
                .code("douyin").displayName("抖音").group(GROUP_CN)
                .authorizeUrl("https://open.douyin.com/platform/oauth/connect/")
                .tokenUrl("https://open.douyin.com/oauth/access_token/")
                .userInfoUrl("https://open.douyin.com/oauth/userinfo/")
                .defaultScope("user_info")
                .clientIdParam("client_key")
                .tokenJson(true)
                .tokenClientIdParam("client_key")
                .tokenClientSecretParam("client_secret")
                .tokenGrantTypeParam("grant_type")
                .tokenAccessTokenPath("data.access_token")
                .tokenOpenIdPath("data.open_id")
                .tokenUnionIdPath("data.union_id")
                .userInfoAuth("header").userInfoHeaderName("access-token")
                .idPath("data.open_id").unionIdPath("data.union_id")
                .nicknamePath("data.nickname").avatarPath("data.avatar")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("dingtalk").displayName("钉钉").group(GROUP_CN)
                .authorizeUrl("https://login.dingtalk.com/oauth2/auth")
                .tokenUrl("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .userInfoUrl("https://api.dingtalk.com/v1.0/contact/users/me")
                .defaultScope("openid")
                .tokenJson(true)
                .tokenClientIdParam("clientId").tokenClientSecretParam("clientSecret")
                .tokenGrantTypeParam("grantType").tokenWithRedirect(false)
                .tokenAccessTokenPath("accessToken")
                .authorizeParams(single("prompt", "consent"))
                .userInfoAuth("header").userInfoHeaderName("x-acs-dingtalk-access-token")
                .idPath("openId").unionIdPath("unionId")
                .nicknamePath("nick").avatarPath("avatarUrl").emailPath("email")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("gitee").displayName("Gitee").group(GROUP_CN)
                .authorizeUrl("https://gitee.com/oauth/authorize")
                .tokenUrl("https://gitee.com/oauth/token")
                .userInfoUrl("https://gitee.com/api/v5/user")
                .defaultScope("user_info")
                .userInfoAuth("query")
                .idPath("id").nicknamePath("name").avatarPath("avatar_url").emailPath("email")
                .build());

        // ==================== 国外 ====================
        list.add(MemberOAuthProviderSpec.builder()
                .code("github").displayName("GitHub").group(GROUP_GLOBAL)
                .authorizeUrl("https://github.com/login/oauth/authorize")
                .tokenUrl("https://github.com/login/oauth/access_token")
                .userInfoUrl("https://api.github.com/user")
                .defaultScope("read:user user:email")
                .tokenHeaders(single("Accept", "application/json"))
                .idPath("id").nicknamePath("login").avatarPath("avatar_url").emailPath("email")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("gitlab").displayName("GitLab").group(GROUP_GLOBAL)
                .authorizeUrl("https://gitlab.com/oauth/authorize")
                .tokenUrl("https://gitlab.com/oauth/token")
                .userInfoUrl("https://gitlab.com/api/v4/user")
                .defaultScope("read_user")
                .idPath("id").nicknamePath("username").avatarPath("avatar_url").emailPath("email")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("google").displayName("Google").group(GROUP_GLOBAL)
                .authorizeUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUrl("https://oauth2.googleapis.com/token")
                .userInfoUrl("https://openidconnect.googleapis.com/v1/userinfo")
                .defaultScope("openid email profile")
                .idPath("sub").nicknamePath("name").avatarPath("picture").emailPath("email")
                .emailVerifiedPath("email_verified")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("microsoft").displayName("Microsoft").group(GROUP_GLOBAL)
                .authorizeUrl("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
                .tokenUrl("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                .userInfoUrl("https://graph.microsoft.com/oidc/userinfo")
                .defaultScope("openid profile email")
                .idPath("sub").nicknamePath("name").avatarPath("picture").emailPath("email")
                .emailVerifiedPath("email_verified")
                .build());
        list.add(spec("apple", "Apple", GROUP_GLOBAL,
                "https://appleid.apple.com/auth/authorize", "", "", "name email", 150));
        list.add(MemberOAuthProviderSpec.builder()
                .code("facebook").displayName("Facebook").group(GROUP_GLOBAL)
                .authorizeUrl("https://www.facebook.com/v19.0/dialog/oauth")
                .tokenUrl("https://graph.facebook.com/v19.0/oauth/access_token")
                .userInfoUrl("https://graph.facebook.com/me")
                .defaultScope("email public_profile")
                .userInfoAuth("query")
                .userInfoQuery("fields=id,name,email,picture.type(large)")
                .idPath("id").nicknamePath("name").avatarPath("picture.data.url").emailPath("email")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("x").displayName("X (Twitter)").group(GROUP_GLOBAL)
                .authorizeUrl("https://twitter.com/i/oauth2/authorize")
                .tokenUrl("https://api.twitter.com/2/oauth2/token")
                .userInfoUrl("https://api.twitter.com/2/users/me")
                .defaultScope("tweet.read users.read offline.access")
                .pkce(true).tokenBasicAuth(true)
                .userInfoQuery("user.fields=profile_image_url")
                .idPath("data.id").nicknamePath("data.name").avatarPath("data.profile_image_url")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("linkedin").displayName("LinkedIn").group(GROUP_GLOBAL)
                .authorizeUrl("https://www.linkedin.com/oauth/v2/authorization")
                .tokenUrl("https://www.linkedin.com/oauth/v2/accessToken")
                .userInfoUrl("https://api.linkedin.com/v2/userinfo")
                .defaultScope("openid profile email")
                .idPath("sub").nicknamePath("name").avatarPath("picture").emailPath("email")
                .emailVerifiedPath("email_verified")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("discord").displayName("Discord").group(GROUP_GLOBAL)
                .authorizeUrl("https://discord.com/oauth2/authorize")
                .tokenUrl("https://discord.com/api/oauth2/token")
                .userInfoUrl("https://discord.com/api/users/@me")
                .defaultScope("identify email")
                .idPath("id").nicknamePath("username").avatarPath("avatar").emailPath("email")
                .avatarUrlTemplate("https://cdn.discordapp.com/avatars/{id}/{avatar}.png")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("line").displayName("LINE").group(GROUP_GLOBAL)
                .authorizeUrl("https://access.line.me/oauth2/v2.1/authorize")
                .tokenUrl("https://api.line.me/oauth2/v2.1/token")
                .userInfoUrl("https://api.line.me/v2/profile")
                .defaultScope("profile openid email")
                .idPath("userId").nicknamePath("displayName").avatarPath("pictureUrl")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("kakao").displayName("Kakao").group(GROUP_GLOBAL)
                .authorizeUrl("https://kauth.kakao.com/oauth/authorize")
                .tokenUrl("https://kauth.kakao.com/oauth/token")
                .userInfoUrl("https://kapi.kakao.com/v2/user/me")
                .defaultScope("profile_nickname profile_image")
                .idPath("id").nicknamePath("kakao_account.profile.nickname")
                .avatarPath("kakao_account.profile.profile_image_url").emailPath("kakao_account.email")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("naver").displayName("Naver").group(GROUP_GLOBAL)
                .authorizeUrl("https://nid.naver.com/oauth2.0/authorize")
                .tokenUrl("https://nid.naver.com/oauth2.0/token")
                .userInfoUrl("https://openapi.naver.com/v1/nid/me")
                .defaultScope("")
                .idPath("response.id").nicknamePath("response.nickname")
                .avatarPath("response.profile_image").emailPath("response.email")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("amazon").displayName("Amazon").group(GROUP_GLOBAL)
                .authorizeUrl("https://www.amazon.com/ap/oa")
                .tokenUrl("https://api.amazon.com/auth/o2/token")
                .userInfoUrl("https://api.amazon.com/user/profile")
                .defaultScope("profile")
                .idPath("user_id").nicknamePath("name").emailPath("email")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("yahoo").displayName("Yahoo").group(GROUP_GLOBAL)
                .authorizeUrl("https://api.login.yahoo.com/oauth2/request_auth")
                .tokenUrl("https://api.login.yahoo.com/oauth2/get_token")
                .userInfoUrl("https://api.login.yahoo.com/openid/v1/userinfo")
                .defaultScope("openid profile email")
                .tokenBasicAuth(true)
                .idPath("sub").nicknamePath("name").avatarPath("picture").emailPath("email")
                .emailVerifiedPath("email_verified")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("reddit").displayName("Reddit").group(GROUP_GLOBAL)
                .authorizeUrl("https://www.reddit.com/api/v1/authorize")
                .tokenUrl("https://www.reddit.com/api/v1/access_token")
                .userInfoUrl("https://oauth.reddit.com/api/v1/me")
                .defaultScope("identity")
                .authorizeParams(single("duration", "permanent"))
                .tokenBasicAuth(true)
                .idPath("id").nicknamePath("name").avatarPath("icon_img")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("slack").displayName("Slack").group(GROUP_GLOBAL)
                .authorizeUrl("https://slack.com/openid/connect/authorize")
                .tokenUrl("https://slack.com/api/openid.connect.token")
                .userInfoUrl("https://slack.com/api/openid.connect.userInfo")
                .defaultScope("openid profile email")
                .idPath("sub").nicknamePath("name").avatarPath("picture").emailPath("email")
                .emailVerifiedPath("email_verified")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("twitch").displayName("Twitch").group(GROUP_GLOBAL)
                .authorizeUrl("https://id.twitch.tv/oauth2/authorize")
                .tokenUrl("https://id.twitch.tv/oauth2/token")
                .userInfoUrl("https://api.twitch.tv/helix/users")
                .defaultScope("user:read:email")
                .clientIdHeaderName("Client-Id")
                .idPath("data[0].id").nicknamePath("data[0].display_name")
                .avatarPath("data[0].profile_image_url").emailPath("data[0].email")
                .build());
        list.add(MemberOAuthProviderSpec.builder()
                .code("spotify").displayName("Spotify").group(GROUP_GLOBAL)
                .authorizeUrl("https://accounts.spotify.com/authorize")
                .tokenUrl("https://accounts.spotify.com/api/token")
                .userInfoUrl("https://api.spotify.com/v1/me")
                .defaultScope("user-read-email user-read-private")
                .tokenBasicAuth(true)
                .idPath("id").nicknamePath("display_name").avatarPath("images[0].url").emailPath("email")
                .build());

        list.sort((left, right) -> left.getCode().compareTo(right.getCode()));
        return list;
    }

    private MemberOAuthProviderSpec spec(String code, String name, String group,
                                         String authorizeUrl, String tokenUrl, String userInfoUrl,
                                         String scope, int sort) {
        return MemberOAuthProviderSpec.builder()
                .code(code).displayName(name).group(group)
                .authorizeUrl(authorizeUrl)
                .tokenUrl(tokenUrl)
                .userInfoUrl(userInfoUrl)
                .defaultScope(scope)
                .build();
    }

    private Map<String, String> single(String key, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
