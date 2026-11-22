package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;

/**
 * 会员短信通道。
 *
 * <p>只负责把找回密码验证码发到手机号，不关心业务规则。开发环境由
 * {@link DevMemberSmsSender} 写日志；生产环境实现本接口并在
 * {@code lx.member.channel.mode=prod} 下装配，业务层不需要改代码。</p>
 */
public interface MemberSmsSender {

    /**
     * 发送找回密码验证码。
     *
     * @param user          会员，供通道记录审计信息（真实通道可忽略）
     * @param mobile        目标手机号
     * @param code          6 位明文验证码，仅用于本次下发
     * @param expireMinutes 有效分钟数，用于短信文案
     */
    void sendResetCode(MemUser user, String mobile, String code, int expireMinutes);
}
