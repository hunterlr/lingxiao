package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 找回密码验证码路由。
 *
 * <p>按申请时使用的账号选择通道：</p>
 * <ol>
 *   <li>账号是邮箱 → 邮件通道；账号是手机号 → 短信通道；</li>
 *   <li>账号是用户名时优先手机号，没有手机号再退邮箱；</li>
 *   <li>两者都没有时只记告警，不抛异常，避免暴露账号是否存在。</li>
 * </ol>
 *
 * <p>真实通道由 {@link MemberSmsSender} / {@link MemberEmailSender} 实现，
 * 业务层只依赖本类，不关心短信服务商或邮件服务器。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class RoutingMemberResetCodeSender implements MemberResetCodeSender {

    private final MemberSmsSender smsSender;
    private final MemberEmailSender emailSender;

    @Override
    public void send(MemUser user, String account, String code, int expireMinutes) {
        String mobile = trimToNull(user.getMobile());
        String email = trimToNull(user.getEmail());
        String usedAccount = trimToNull(account);

        if (email != null && usedAccount != null && email.equalsIgnoreCase(usedAccount)) {
            emailSender.sendResetCode(user, email, code, expireMinutes);
            return;
        }
        if (mobile != null && usedAccount != null && mobile.equals(usedAccount)) {
            smsSender.sendResetCode(user, mobile, code, expireMinutes);
            return;
        }
        if (mobile != null) {
            smsSender.sendResetCode(user, mobile, code, expireMinutes);
            return;
        }
        if (email != null) {
            emailSender.sendResetCode(user, email, code, expireMinutes);
            return;
        }
        log.warn("会员没有可用的手机号或邮箱，找回密码验证码未下发: memberId={}, username={}",
                user.getMemberId(), user.getUsername());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
