package com.lingxiao.modules.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberLoginResponse;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.oauth.MemberOAuthProviderRegistry;
import com.lingxiao.modules.member.oauth.MemberOAuthProviderSpec;
import com.lingxiao.modules.member.oauth.MemberOAuthState;
import com.lingxiao.modules.member.oauth.MemberOAuthStateStore;
import com.lingxiao.modules.member.oauth.MemberOAuthTicket;
import com.lingxiao.modules.member.oauth.OAuthJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 第三方登录流程编排测试。
 *
 * <p>回调失败必须 302 回门户并带稳定短码，不能把浏览器停在 500 或 JSON；
 * ticket 只能换一次；站内跳转目标不能被拼成开放重定向。</p>
 */
@DisplayName("MemberOAuthService 第三方登录编排")
class MemberOAuthServiceTest {

    private MemberOAuthConfigService configService;
    private MemberOAuthAccountService accountService;
    private MemberOAuthProviderRegistry registry;
    private MemberOAuthStateStore stateStore;
    private MemberLevelService levelService;
    private MemberTokenIssuer tokenIssuer;
    private MemberOAuthService service;

    @BeforeEach
    void setUp() {
        configService = mock(MemberOAuthConfigService.class);
        accountService = mock(MemberOAuthAccountService.class);
        registry = mock(MemberOAuthProviderRegistry.class);
        stateStore = mock(MemberOAuthStateStore.class);
        levelService = mock(MemberLevelService.class);
        tokenIssuer = mock(MemberTokenIssuer.class);
        OAuthJson json = new OAuthJson(new ObjectMapper());
        service = new MemberOAuthService(configService, accountService, registry,
                stateStore, levelService, tokenIssuer, json);
        when(configService.portalBaseUrl()).thenReturn("http://127.0.0.1:3000");
    }

    @Test
    @DisplayName("state 过期：302 回登录页并带 state_invalid")
    void expiredStateRedirectsToLogin() {
        when(stateStore.consumeState("bad")).thenReturn(null);

        String target = service.handleCallback("github", "code", "bad");

        assertThat(target).isEqualTo("http://127.0.0.1:3000/login?oauthError=state_invalid&provider=github");
    }

    @Test
    @DisplayName("绑定模式下失败：302 回账户页，不丢登录态")
    void bindFailureRedirectsToAccount() {
        MemberOAuthState state = new MemberOAuthState();
        state.setProvider("wechat_mp");
        state.setMode(MemberOAuthState.MODE_BIND);
        state.setMemberId(9L);
        when(stateStore.consumeState("s1")).thenReturn(state);
        when(configService.requireEnabled("wechat_mp")).thenThrow(
                ServiceException.keyed(ErrorCode.FORBIDDEN, "error.1003.member.oauthProviderDisabled", "微信"));

        String target = service.handleCallback("wechat_mp", "code", "s1");

        assertThat(target).isEqualTo("http://127.0.0.1:3000/account?oauthError=provider_disabled&provider=wechat_mp");
    }

    @Test
    @DisplayName("总开关关闭：回调给出 disabled 短码")
    void globalDisabledCode() {
        MemberOAuthState state = new MemberOAuthState();
        state.setProvider("github");
        state.setMode(MemberOAuthState.MODE_LOGIN);
        when(stateStore.consumeState("s2")).thenReturn(state);
        when(configService.requireEnabled("github")).thenThrow(
                ServiceException.keyed(ErrorCode.FORBIDDEN, "error.1003.member.oauthDisabled"));

        String target = service.handleCallback("github", "code", "s2");

        assertThat(target).contains("oauthError=disabled").contains("provider=github");
    }

    @Test
    @DisplayName("ticket 成功换 Token，返回会话")
    void exchangeIssuesToken() {
        when(configService.isGlobalEnabled()).thenReturn(true);
        when(stateStore.consumeTicket("t1")).thenReturn(new MemberOAuthTicket("gitee", 7L));
        MemUser user = new MemUser();
        user.setMemberId(7L);
        when(accountService.requireActiveMember(7L)).thenReturn(user);
        when(levelService.nameOf(any())).thenReturn("普通会员");
        MemberLoginResponse response = new MemberLoginResponse();
        when(tokenIssuer.issue(any(MemUser.class), anyString())).thenReturn(response);

        assertThat(service.exchange("t1")).isSameAs(response);
    }

    @Test
    @DisplayName("ticket 无效：抛参数错误，不签发 Token")
    void exchangeRejectsUsedTicket() {
        when(configService.isGlobalEnabled()).thenReturn(true);
        when(stateStore.consumeTicket("used")).thenReturn(null);

        assertThatThrownBy(() -> service.exchange("used"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.oauthTicketInvalid"));
    }

    @Test
    @DisplayName("总开关关闭：exchange 直接拒绝，防止旧 ticket 绕过开关")
    void exchangeRejectedWhenDisabled() {
        when(configService.isGlobalEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.exchange("t1"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1003.member.oauthDisabled"));
    }

    @Test
    @DisplayName("小程序登录：普通平台不能绕过 state 走 mini-login")
    void miniLoginRejectsBrowserProvider() {
        when(registry.spec("github")).thenReturn(MemberOAuthProviderSpec.builder()
                .code("github").browser(true).build());

        assertThatThrownBy(() -> service.miniLogin("github", "code", null, null))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.oauthMiniOnly"));
    }

    @Test
    @DisplayName("小程序登录：缺 code 直接拒绝")
    void miniLoginRequiresCode() {
        when(registry.spec("wechat_mini")).thenReturn(MemberOAuthProviderSpec.builder()
                .code("wechat_mini").browser(false).build());

        assertThatThrownBy(() -> service.miniLogin("wechat_mini", " ", null, null))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.oauthCodeRequired"));
    }

    @Test
    @DisplayName("跳转目标只允许站内路径")
    void safeRedirectRejectsExternal() {
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "safeRedirect", (String) null))
                .isEqualTo("/account");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "safeRedirect", "https://evil.example"))
                .isEqualTo("/account");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "safeRedirect", "//evil.example"))
                .isEqualTo("/account");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "safeRedirect", "/favorites?page=2"))
                .isEqualTo("/favorites?page=2");
    }

    @Test
    @DisplayName("绑定列表 / 解绑要求会员登录")
    void bindingsRequireLogin() {
        assertThatThrownBy(() -> service.bindings())
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED.getCode()));
    }
}
