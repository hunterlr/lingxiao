package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 找回密码验证码通道路由测试。
 *
 * <p>核心规则：账号是邮箱走邮件、账号是手机号走短信、账号是用户名时优先手机号；
 * 两者都没有时不抛异常，避免暴露账号是否存在。</p>
 */
@DisplayName("RoutingMemberResetCodeSender 验证码通道")
class RoutingMemberResetCodeSenderTest {

    private MemberSmsSender smsSender;
    private MemberEmailSender emailSender;
    private RoutingMemberResetCodeSender sender;

    @BeforeEach
    void setUp() {
        smsSender = mock(MemberSmsSender.class);
        emailSender = mock(MemberEmailSender.class);
        sender = new RoutingMemberResetCodeSender(smsSender, emailSender);
    }

    private static MemUser user() {
        MemUser user = new MemUser();
        user.setMemberId(100L);
        user.setUsername("alice");
        user.setMobile("13800000000");
        user.setEmail("alice@example.com");
        return user;
    }

    @Test
    @DisplayName("用手机号申请：走短信通道")
    void mobileAccountUsesSms() {
        MemUser user = user();

        sender.send(user, "13800000000", "123456", 10);

        verify(smsSender).sendResetCode(user, "13800000000", "123456", 10);
        verify(emailSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("用邮箱申请：走邮件通道（大小写不敏感）")
    void emailAccountUsesEmail() {
        MemUser user = user();

        sender.send(user, "ALICE@example.com", "123456", 10);

        verify(emailSender).sendResetCode(user, "alice@example.com", "123456", 10);
        verify(smsSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("用用户名申请：有手机号优先短信")
    void usernamePrefersMobile() {
        MemUser user = user();

        sender.send(user, "alice", "123456", 10);

        verify(smsSender).sendResetCode(eq(user), eq("13800000000"), eq("123456"), eq(10));
    }

    @Test
    @DisplayName("没有手机号：回退邮件通道")
    void fallsBackToEmail() {
        MemUser user = user();
        user.setMobile(null);

        sender.send(user, "alice", "123456", 10);

        verify(emailSender).sendResetCode(user, "alice@example.com", "123456", 10);
    }

    @Test
    @DisplayName("手机号与邮箱都为空：不抛异常，仅记录告警")
    void noChannelDoesNotThrow() {
        MemUser user = user();
        user.setMobile(" ");
        user.setEmail(null);

        assertThatCode(() -> sender.send(user, "alice", "123456", 10)).doesNotThrowAnyException();
        verify(smsSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
        verify(emailSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
    }
}
