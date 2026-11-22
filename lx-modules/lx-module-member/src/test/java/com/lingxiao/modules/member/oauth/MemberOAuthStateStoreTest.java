package com.lingxiao.modules.member.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第三方登录 state / ticket 存储测试。
 *
 * <p>直接用 Sa-Token 默认 DAO（单机内存，接 Redis 后自动共享）。重点锁定：
 * 一次性消费、字段完整回读、非法 key 不抛异常。</p>
 */
@DisplayName("MemberOAuthStateStore 第三方登录临时凭证")
class MemberOAuthStateStoreTest {

    private final MemberOAuthStateStore store = new MemberOAuthStateStore(new ObjectMapper());

    @Test
    @DisplayName("state 可完整回读，且只能消费一次")
    void stateIsSingleUse() {
        MemberOAuthState state = new MemberOAuthState();
        state.setProvider("github");
        state.setMode(MemberOAuthState.MODE_BIND);
        state.setMemberId(123L);
        state.setRedirect("/account?tab=security");
        state.setCodeVerifier("verifier-value");

        String key = store.createState(state);
        assertThat(key).isNotBlank();

        MemberOAuthState parsed = store.consumeState(key);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getProvider()).isEqualTo("github");
        assertThat(parsed.isBind()).isTrue();
        assertThat(parsed.getMemberId()).isEqualTo(123L);
        assertThat(parsed.getRedirect()).isEqualTo("/account?tab=security");
        assertThat(parsed.getCodeVerifier()).isEqualTo("verifier-value");

        // 第二次消费必须失败，防止回调被重放
        assertThat(store.consumeState(key)).isNull();
    }

    @Test
    @DisplayName("login 模式不携带会员 ID，空 redirect 也能回读")
    void loginStateWithoutMember() {
        MemberOAuthState state = new MemberOAuthState();
        state.setProvider("wechat_mp");
        state.setMode(MemberOAuthState.MODE_LOGIN);

        MemberOAuthState parsed = store.consumeState(store.createState(state));
        assertThat(parsed).isNotNull();
        assertThat(parsed.isBind()).isFalse();
        assertThat(parsed.getMemberId()).isNull();
        assertThat(parsed.getRedirect()).isNull();
        assertThat(parsed.getCodeVerifier()).isNull();
    }

    @Test
    @DisplayName("ticket 只存会员 ID，且只能换一次")
    void ticketIsSingleUse() {
        String ticket = store.createTicket("gitee", 456L);

        MemberOAuthTicket parsed = store.consumeTicket(ticket);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getProvider()).isEqualTo("gitee");
        assertThat(parsed.getMemberId()).isEqualTo(456L);

        assertThat(store.consumeTicket(ticket)).isNull();
    }

    @Test
    @DisplayName("bindTicket 保存完整第三方信息，且只能消费一次")
    void bindTicketIsSingleUse() {
        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider("github");
        info.setOpenId("open-123");
        info.setNickname("octocat");
        info.setEmail("a@b.com");
        info.setEmailVerified(true);

        MemberOAuthBindTicket ticket = new MemberOAuthBindTicket();
        ticket.setProvider("github");
        ticket.setTargetMemberId(789L);
        ticket.setInfo(info);

        String key = store.createBindTicket(ticket);
        assertThat(key).isNotBlank();

        MemberOAuthBindTicket parsed = store.consumeBindTicket(key);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getProvider()).isEqualTo("github");
        assertThat(parsed.getTargetMemberId()).isEqualTo(789L);
        assertThat(parsed.getInfo().getOpenId()).isEqualTo("open-123");
        assertThat(parsed.getInfo().isEmailVerified()).isTrue();

        assertThat(store.consumeBindTicket(key)).isNull();
    }

    @Test
    @DisplayName("空 key / 伪造 key 返回 null，不抛异常")
    void invalidKeyIsIgnored() {
        assertThat(store.consumeState(null)).isNull();
        assertThat(store.consumeState("  ")).isNull();
        assertThat(store.consumeState("not-exists")).isNull();
        assertThat(store.consumeTicket(null)).isNull();
        assertThat(store.consumeTicket("not-exists")).isNull();
        assertThat(store.consumeBindTicket(null)).isNull();
        assertThat(store.consumeBindTicket("not-exists")).isNull();
    }
}
