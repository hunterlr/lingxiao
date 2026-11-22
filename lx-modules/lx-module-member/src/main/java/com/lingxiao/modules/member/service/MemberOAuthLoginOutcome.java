package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;
import lombok.Getter;

/**
 * 第三方登录决策结果。
 *
 * <p>两种结果互斥：</p>
 * <ul>
 *   <li>{@link #getMember()}：已绑定或允许自动建号，直接签发会员会话；</li>
 *   <li>{@link #getExistingMember()}：平台返回的已验证邮箱 / 手机号命中已有会员，
 *       不自动登录，先让用户登录原账号再绑定。</li>
 * </ul>
 */
@Getter
public class MemberOAuthLoginOutcome {

    private final MemUser member;

    private final MemUser existingMember;

    private MemberOAuthLoginOutcome(MemUser member, MemUser existingMember) {
        this.member = member;
        this.existingMember = existingMember;
    }

    public static MemberOAuthLoginOutcome loggedIn(MemUser member) {
        return new MemberOAuthLoginOutcome(member, null);
    }

    public static MemberOAuthLoginOutcome requiresBinding(MemUser existingMember) {
        return new MemberOAuthLoginOutcome(null, existingMember);
    }

    public boolean requiresBinding() {
        return existingMember != null;
    }
}
