package com.lingxiao.modules.member.oauth;

import lombok.Data;

/**
 * 一次第三方授权的服务端状态。
 *
 * <p>只存在服务端（Sa-Token 存储），浏览器只拿到随机 state；回调时按 state
 * 取回服务商、模式与目标会员，不信任 URL 上的任何身份参数。</p>
 */
@Data
public class MemberOAuthState {

    /** login：登录 / 自动建号；bind：已登录会员绑定新平台 */
    public static final String MODE_LOGIN = "login";
    public static final String MODE_BIND = "bind";

    private String provider;

    private String mode;

    /** bind 模式下由发起请求的会员 Session 写入 */
    private Long memberId;

    /** 成功后门户要跳回的站内路径 */
    private String redirect;

    /** PKCE code_verifier；X / Twitter 等平台需要，其它平台为空 */
    private String codeVerifier;

    public boolean isBind() {
        return MODE_BIND.equals(mode);
    }
}
