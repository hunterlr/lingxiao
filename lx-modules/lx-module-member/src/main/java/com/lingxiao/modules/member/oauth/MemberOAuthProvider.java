package com.lingxiao.modules.member.oauth;

/**
 * 第三方登录 Provider。
 *
 * <p>生命周期：授权地址由浏览器跳转，回调由服务端换 Token 并归一化用户信息。
 * 所有实现都只接收 {@link MemberOAuthProviderConfig}，不直接读数据库。</p>
 */
public interface MemberOAuthProvider {

    String code();

    String displayName();

    /** cn / global */
    String group();

    /**
     * 构建授权跳转地址。
     *
     * @param state       一次性 state
     * @param codeChallenge PKCE challenge；不需要时忽略
     */
    String authorizeUrl(MemberOAuthProviderConfig config, String state, String codeChallenge);

    /**
     * 用授权码换用户信息。
     *
     * @param codeVerifier PKCE verifier；不需要时忽略
     */
    OAuthUserInfo exchange(MemberOAuthProviderConfig config, String code, String codeVerifier);
}
