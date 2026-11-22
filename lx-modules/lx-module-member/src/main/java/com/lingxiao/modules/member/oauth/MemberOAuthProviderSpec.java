package com.lingxiao.modules.member.oauth;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标准 OAuth2 / OIDC 服务商描述。
 *
 * <p>把“授权地址、换 Token 方式、用户信息字段路径”等差异收敛成数据，
 * 绝大多数国外服务商只需要在这里加一条描述即可，不再写单独的 Provider 类。
 * 微信、QQ、支付宝、Apple 这类流程不标准的平台仍由独立类实现。</p>
 */
@Data
@Builder
public class MemberOAuthProviderSpec {

    private String code;

    private String displayName;

    /** cn：国内；global：国外 */
    private String group;

    private String authorizeUrl;

    private String tokenUrl;

    private String userInfoUrl;

    private String defaultScope;

    /** 授权参数名，默认 client_id / redirect_uri / scope / state / response_type */
    @Builder.Default
    private String clientIdParam = "client_id";

    @Builder.Default
    private String redirectParam = "redirect_uri";

    @Builder.Default
    private String scopeParam = "scope";

    @Builder.Default
    private String stateParam = "state";

    @Builder.Default
    private String responseTypeParam = "response_type";

    /** 额外的固定授权参数，例如 prompt=consent */
    @Builder.Default
    private Map<String, String> authorizeParams = new LinkedHashMap<>();

    /** Token 请求体：false=表单，true=JSON */
    private boolean tokenJson;

    /** Token 请求是否使用 HTTP Basic 认证 */
    private boolean tokenBasicAuth;

    @Builder.Default
    private String tokenClientIdParam = "client_id";

    @Builder.Default
    private String tokenClientSecretParam = "client_secret";

    @Builder.Default
    private String tokenCodeParam = "code";

    @Builder.Default
    private String tokenGrantTypeParam = "grant_type";

    @Builder.Default
    private String tokenGrantTypeValue = "authorization_code";

    /** 是否在换 Token 时带 redirect_uri，默认带 */
    @Builder.Default
    private boolean tokenWithRedirect = true;

    /** 额外 Token 参数 */
    @Builder.Default
    private Map<String, String> tokenParams = new LinkedHashMap<>();

    /** 额外 Token 请求头 */
    @Builder.Default
    private Map<String, String> tokenHeaders = new LinkedHashMap<>();

    /** Token 响应中 access_token / openId 的字段路径 */
    @Builder.Default
    private String tokenAccessTokenPath = "access_token";

    private String tokenOpenIdPath;

    private String tokenUnionIdPath;

    /** 用户信息认证方式：bearer / query / header */
    @Builder.Default
    private String userInfoAuth = "bearer";

    private String userInfoHeaderName;

    @Builder.Default
    private Map<String, String> userInfoHeaders = new LinkedHashMap<>();

    /** 用户信息额外 query，例如 fields=id,name,email */
    private String userInfoQuery;

    /** 用户信息字段路径，支持 data.id / images[0].url 这种写法 */
    @Builder.Default
    private String idPath = "id";

    private String unionIdPath;

    private String nicknamePath;

    private String avatarPath;

    private String emailPath;

    /** 邮箱是否已验证的字段路径，例如 email_verified / verified_email */
    private String emailVerifiedPath;

    private String mobilePath;

    /** 手机号是否已验证的字段路径 */
    private String mobileVerifiedPath;

    /** 头像需要拼接时使用，占位符 {id} / {avatar} */
    private String avatarUrlTemplate;

    /** 是否使用 PKCE（X / Twitter） */
    private boolean pkce;

    /** 用户信息请求需要固定带上的 Client-Id 头（Twitch） */
    private String clientIdHeaderName;

    /**
     * 是否支持浏览器跳转授权。
     *
     * <p>微信小程序没有网页授权入口，需要客户端先 {@code wx.login()} 再把 code
     * 交给 {@code POST /api/app/auth/oauth/mini-login}；这类平台设为 false，
     * 门户登录页不展示按钮，避免点进去落到空白页。</p>
     */
    @Builder.Default
    private boolean browser = true;
}
