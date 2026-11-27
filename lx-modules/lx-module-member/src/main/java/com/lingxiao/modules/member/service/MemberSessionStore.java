package com.lingxiao.modules.member.service;

import cn.dev33.satoken.session.SaSession;
import com.lingxiao.common.security.util.StpMemberUtil;
import com.lingxiao.modules.member.security.MemberLoginHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * 会员会话存储访问层。
 *
 * <p>把 Sa-Token 的静态调用收在一处，{@link MemberSessionService} 只依赖本类，
 * 单元测试直接 mock 即可，不需要启动 Sa-Token 上下文。</p>
 *
 * <p><b>安全约定：</b>Token 值只在服务端内部流转，接口层用哈希句柄（见
 * {@link MemberSessionService}）标识会话，绝不下发原始 Token。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberSessionStore {

    /** 指定会员当前有效 Token 列表；拿不到会话时返回空列表。 */
    public List<String> tokens(Long memberId) {
        if (memberId == null) {
            return Collections.emptyList();
        }
        try {
            List<String> values = StpMemberUtil.getTokenValueListByLoginId(memberId);
            return values == null ? Collections.emptyList() : values;
        } catch (RuntimeException e) {
            log.warn("读取会员会话列表失败: memberId={}, error={}", memberId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Token 剩余有效期（秒）；<=0 视为已过期。 */
    public long timeout(String tokenValue) {
        try {
            return StpMemberUtil.getTokenTimeout(tokenValue);
        } catch (RuntimeException e) {
            return -2;
        }
    }

    /** 当前请求使用的 Token；非 Web 线程返回 null。 */
    public String currentToken() {
        try {
            return StpMemberUtil.getTokenValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void logout(String tokenValue) {
        if (tokenValue == null) {
            return;
        }
        StpMemberUtil.logoutByTokenValue(tokenValue);
    }

    /** Token-Session 里的登录上下文；Session 不存在时返回空对象。 */
    public SessionContext context(String tokenValue) {
        SaSession session;
        try {
            session = StpMemberUtil.getTokenSessionByToken(tokenValue);
        } catch (RuntimeException e) {
            return new SessionContext(null, null, null);
        }
        if (session == null) {
            return new SessionContext(null, null, null);
        }
        return new SessionContext(
                stringValue(session.get(MemberLoginHelper.SESSION_LOGIN_IP)),
                stringValue(session.get(MemberLoginHelper.SESSION_USER_AGENT)),
                timeValue(session.get(MemberLoginHelper.SESSION_LOGIN_TIME)));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime timeValue(Object value) {
        if (!(value instanceof Number)) {
            return null;
        }
        long millis = ((Number) value).longValue();
        if (millis <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /** 一次会话的登录上下文。 */
    public static final class SessionContext {
        private final String loginIp;
        private final String userAgent;
        private final LocalDateTime loginTime;

        public SessionContext(String loginIp, String userAgent, LocalDateTime loginTime) {
            this.loginIp = loginIp;
            this.userAgent = userAgent;
            this.loginTime = loginTime;
        }

        public String getLoginIp() {
            return loginIp;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public LocalDateTime getLoginTime() {
            return loginTime;
        }
    }
}