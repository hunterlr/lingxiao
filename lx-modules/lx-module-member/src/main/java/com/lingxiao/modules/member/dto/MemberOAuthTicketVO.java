package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 小程序登录结果。
 *
 * <p>登录模式返回一次性 {@code ticket}，由客户端立即 POST
 * {@code /api/app/auth/oauth/exchange} 换会员 Token；绑定模式不返回 ticket，
 * 直接把 {@code bound=true} 告诉客户端。Token 与 ticket 都不出现在 URL。</p>
 */
@Data
public class MemberOAuthTicketVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;

    /** 一次性登录凭证；绑定模式下为空 */
    private String ticket;

    /** 成功后客户端 / 门户建议跳转的站内路径 */
    private String redirect;

    /** true 表示本次是绑定操作，未签发新会话 */
    private boolean bound;

    /**
     * true 表示第三方返回的已验证邮箱 / 手机号命中已有会员。
     *
     * <p>此时不签发会话，客户端应提示用户先用原账号登录，再调用
     * {@code /api/app/auth/oauth/bind-ticket} 完成绑定。</p>
     */
    private boolean bindRequired;

    /** 待绑定凭证；{@code bindRequired=true} 时返回，两分钟有效且只能用一次 */
    private String bindTicket;
}
