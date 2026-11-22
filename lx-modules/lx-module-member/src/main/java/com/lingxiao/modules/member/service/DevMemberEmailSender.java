package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;
import lombok.extern.slf4j.Slf4j;

/**
 * 开发环境邮件通道：只写应用日志，不真正发邮件。
 *
 * <p>日志格式保留 {@code memberId / username / code / expireMinutes}，
 * 便于本地验收脚本直接从日志读取验证码。生产必须替换为真实通道。</p>
 */
@Slf4j
public class DevMemberEmailSender implements MemberEmailSender {

    @Override
    public void sendResetCode(MemUser user, String email, String code, int expireMinutes) {
        log.info("会员找回密码邮件验证码（开发通道）: memberId={}, username={}, code={}, expireMinutes={}, email={}",
                user.getMemberId(), user.getUsername(), code, expireMinutes, email);
    }
}
