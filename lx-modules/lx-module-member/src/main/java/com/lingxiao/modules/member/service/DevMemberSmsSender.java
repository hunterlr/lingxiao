package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;
import lombok.extern.slf4j.Slf4j;

/**
 * 开发环境短信通道：只写应用日志，不真正发短信。
 *
 * <p>日志格式保留 {@code memberId / username / code / expireMinutes}，
 * 便于本地验收脚本直接从日志读取验证码。生产必须替换为真实通道。</p>
 */
@Slf4j
public class DevMemberSmsSender implements MemberSmsSender {

    @Override
    public void sendResetCode(MemUser user, String mobile, String code, int expireMinutes) {
        log.info("会员找回密码短信验证码（开发通道）: memberId={}, username={}, code={}, expireMinutes={}, mobile={}",
                user.getMemberId(), user.getUsername(), code, expireMinutes, mobile);
    }
}
