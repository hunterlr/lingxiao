package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 日志版验证码下发。
 *
 * <p>没有接短信 / 邮件通道时，验证码只写入应用日志；生产上线前必须替换为
 * 真实通道（或关闭找回密码入口），不能把日志当短信发给用户。</p>
 *
 * <p>日志里同时记录会员 ID 与账号，便于运维按工单排查；验证码 10 分钟有效。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class LogMemberResetCodeSender implements MemberResetCodeSender {

    @Override
    public void send(MemUser user, String code, int expireMinutes) {
        log.info("会员找回密码验证码（开发通道）: memberId={}, username={}, code={}, expireMinutes={}",
                user.getMemberId(), user.getUsername(), code, expireMinutes);
    }
}
