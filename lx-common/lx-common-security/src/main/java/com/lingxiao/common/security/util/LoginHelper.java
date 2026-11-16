package com.lingxiao.common.security.util;

import cn.dev33.satoken.session.SaSession;

/**
 * 登录用户上下文。
 *
 * <p>从平台域 Token 的 Session 中读取当前用户信息。Session 在登录成功时写入，
 * 不要在每次请求都查库；用户改名 / 改昵称时由用户服务主动刷新 Session。</p>
 */
public final class LoginHelper {

    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_USERNAME = "username";
    public static final String SESSION_NICKNAME = "nickname";

    // 以下两个 Key 写在 Token-Session（不是账号 Session）：
    // 同一账号允许并发登录，不同 Token 的登录 IP / 浏览器必须各记各的，
    // 在线用户页才能准确展示“哪一个会话来自哪里”。
    public static final String SESSION_LOGIN_IP = "loginIp";
    public static final String SESSION_USER_AGENT = "userAgent";

    private LoginHelper() {
    }

    public static Long getUserId() {
        String id = StpPlatformUtil.getLoginIdAsString();
        return id == null ? null : Long.valueOf(id);
    }

    public static String getUsername() {
        return getSessionString(SESSION_USERNAME);
    }

    public static String getNickname() {
        return getSessionString(SESSION_NICKNAME);
    }

    public static void setLoginUser(Long userId, String username, String nickname) {
        SaSession session = StpPlatformUtil.getSession();
        session.set(SESSION_USER_ID, userId);
        session.set(SESSION_USERNAME, username);
        session.set(SESSION_NICKNAME, nickname);
    }

    /**
     * 把本次登录的 IP / 浏览器写入当前 Token 的 Token-Session。
     *
     * <p>必须与 {@link #setLoginUser} 分开：用户名 / 昵称属于账号，
     * IP / 浏览器属于某一个 Token。同一账号在办公室和家里同时登录时，
     * 两行在线记录应该显示各自的来源。</p>
     */
    public static void setLoginContext(String loginIp, String userAgent) {
        SaSession tokenSession = StpPlatformUtil.getTokenSession();
        if (tokenSession == null) {
            return;
        }
        tokenSession.set(SESSION_LOGIN_IP, loginIp);
        tokenSession.set(SESSION_USER_AGENT, userAgent);
    }

    /**
     * 从指定 Session 读字符串，Session 为空或值缺失返回 {@code null}。
     *
     * <p>监控列表要读“别人的 Session”，不能复用只读当前上下文的 getter。</p>
     */
    public static String readString(SaSession session, String key) {
        if (session == null) {
            return null;
        }
        try {
            Object value = session.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getSessionString(String key) {
        try {
            SaSession session = StpPlatformUtil.getSession();
            if (session == null) {
                return null;
            }
            Object value = session.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }
}
