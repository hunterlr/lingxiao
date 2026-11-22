package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;

/**
 * 找回密码验证码下发入口。
 *
 * <p>业务层只调用本接口；具体走短信还是邮件由
 * {@link RoutingMemberResetCodeSender} 根据账号与会员资料决定。
 * 生产接入真实通道时实现 {@link MemberSmsSender} / {@link MemberEmailSender}，
 * 不需要修改会员账号服务。</p>
 */
public interface MemberResetCodeSender {

    /**
     * 下发找回密码验证码。
     *
     * @param user          会员
     * @param account       申请时使用的账号（用户名 / 手机号 / 邮箱），用于选择通道
     * @param code          6 位明文验证码
     * @param expireMinutes 有效分钟数
     */
    void send(MemUser user, String account, String code, int expireMinutes);
}
