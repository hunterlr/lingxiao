package com.lingxiao.modules.member.oauth;

import lombok.Data;

/**
 * 待绑定第三方账号的一次性凭证内容。
 *
 * <p>只保存在服务端 {@code SaTokenDao} 中，两分钟有效且只能使用一次；
 * 门户 URL 里只出现随机 ticket，不包含 openId / Token。</p>
 */
@Data
public class MemberOAuthBindTicket {

    private String provider;

    /** 命中的已有会员；必须用这个账号登录后才能完成绑定 */
    private Long targetMemberId;

    /** 回调时拿到的第三方用户信息快照，绑定阶段不再回查平台 */
    private OAuthUserInfo info;
}
