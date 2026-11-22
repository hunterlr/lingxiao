package com.lingxiao.modules.member.oauth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 第三方登录的一次性 state 与 ticket 存储。
 *
 * <p>使用 Sa-Token 的 {@link SaTokenDao} 而不是进程内 Map：单机默认落内存，
 * 后续接入 Redis 时自动变成跨实例共享，不用改业务代码。</p>
 *
 * <p>state 有效期 10 分钟，防止用户在授权页停留过久后回调失败；
 * ticket 有效期 2 分钟且只能换一次，门户拿到后立刻 POST 换 Token。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberOAuthStateStore {

    private static final String STATE_PREFIX = "lx:member:oauth:state:";
    private static final String TICKET_PREFIX = "lx:member:oauth:ticket:";
    private static final String BIND_TICKET_PREFIX = "lx:member:oauth:bind:";
    private static final int STATE_TTL_SECONDS = 10 * 60;
    private static final int TICKET_TTL_SECONDS = 2 * 60;
    private static final int BIND_TICKET_TTL_SECONDS = 2 * 60;
    private static final String SEPARATOR = "|";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;

    /** 生成并保存 state，返回给授权地址。 */
    public String createState(MemberOAuthState state) {
        String key = randomToken();
        String value = state.getProvider() + SEPARATOR
                + state.getMode() + SEPARATOR
                + (state.getMemberId() == null ? "" : state.getMemberId()) + SEPARATOR
                + base64(state.getRedirect()) + SEPARATOR
                + (state.getCodeVerifier() == null ? "" : state.getCodeVerifier());
        dao().set(STATE_PREFIX + key, value, STATE_TTL_SECONDS);
        return key;
    }

    /** 读取并作废 state；不存在 / 已过期 / 已使用返回 null。 */
    public MemberOAuthState consumeState(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String storeKey = STATE_PREFIX + key.trim();
        SaTokenDao dao = dao();
        String value = dao.get(storeKey);
        if (value == null) {
            return null;
        }
        dao.delete(storeKey);
        String[] parts = value.split("\\" + SEPARATOR, -1);
        if (parts.length < 4) {
            return null;
        }
        MemberOAuthState state = new MemberOAuthState();
        state.setProvider(parts[0]);
        state.setMode(parts[1]);
        state.setMemberId(StringUtils.hasText(parts[2]) ? Long.valueOf(parts[2]) : null);
        state.setRedirect(unbase64(parts[3]));
        state.setCodeVerifier(parts.length > 4 && StringUtils.hasText(parts[4]) ? parts[4] : null);
        return state;
    }

    /** 保存一次性 ticket；只存会员 ID 与来源平台，不存 Token。 */
    public String createTicket(String provider, Long memberId) {
        String key = randomToken();
        dao().set(TICKET_PREFIX + key, provider + SEPARATOR + memberId, TICKET_TTL_SECONDS);
        return key;
    }

    /** 读取并作废 ticket。 */
    public MemberOAuthTicket consumeTicket(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String storeKey = TICKET_PREFIX + key.trim();
        SaTokenDao dao = dao();
        String value = dao.get(storeKey);
        if (value == null) {
            return null;
        }
        dao.delete(storeKey);
        String[] parts = value.split("\\" + SEPARATOR, -1);
        if (parts.length < 2 || !StringUtils.hasText(parts[1])) {
            return null;
        }
        return new MemberOAuthTicket(parts[0], Long.valueOf(parts[1]));
    }

    /**
     * 保存待绑定第三方账号的一次性凭证。
     *
     * <p>与登录 ticket 分开前缀、分开 TTL：绑定凭证只在“未登录会话”场景使用，
     * 内容包含 openId 与用户信息快照，序列化成 JSON 存服务端，URL 只带随机串。</p>
     */
    public String createBindTicket(MemberOAuthBindTicket ticket) {
        String key = randomToken();
        try {
            String value = objectMapper.writeValueAsString(ticket);
            dao().set(BIND_TICKET_PREFIX + key, value, BIND_TICKET_TTL_SECONDS);
            return key;
        } catch (Exception e) {
            log.error("保存第三方绑定凭证失败: provider={}", ticket.getProvider(), e);
            return null;
        }
    }

    /** 读取并作废绑定凭证；不存在 / 已过期 / 已使用 / 内容损坏都返回 null。 */
    public MemberOAuthBindTicket consumeBindTicket(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String storeKey = BIND_TICKET_PREFIX + key.trim();
        SaTokenDao dao = dao();
        String value = dao.get(storeKey);
        if (value == null) {
            return null;
        }
        dao.delete(storeKey);
        try {
            return objectMapper.readValue(value, MemberOAuthBindTicket.class);
        } catch (Exception e) {
            log.warn("第三方绑定凭证内容无法解析，按无效处理");
            return null;
        }
    }

    private SaTokenDao dao() {
        return SaManager.getSaTokenDao();
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String base64(String value) {
        String text = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String unbase64(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
