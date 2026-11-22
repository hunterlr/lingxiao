package com.lingxiao.modules.member.security;

import cn.dev33.satoken.session.SaSession;
import com.lingxiao.common.security.util.StpMemberUtil;

/**
 * 会员登录上下文。
 *
 * <p>从会员域 Token 的 Session 读取当前会员；与 {@code LoginHelper}
 * （平台域）完全分开，避免一次请求里拿错域。</p>
 *
 * <p>Session 只在登录时写入，不要在每次请求查库；会员改昵称后由
 * 个人中心主动刷新。</p>
 */
public final class MemberLoginHelper {

    public static final String SESSION_MEMBER_ID = "memberId";
    public static final String SESSION_USERNAME = "username";
    public static final String SESSION_NICKNAME = "nickname";
    public static final String SESSION_LEVEL_ID = "levelId";

    /** 写在 Token-Session，区分同一账号不同设备 */
    public static final String SESSION_LOGIN_IP = "loginIp";
    public static final String SESSION_USER_AGENT = "userAgent";

    private MemberLoginHelper() {
    }

    public static Long getMemberId() {
        try {
            String id = StpMemberUtil.getLoginIdAsString();
            return id == null ? null : Long.valueOf(id);
        } catch (RuntimeException e) {
            // 非 Web 线程（定时任务 / 单测）拿不到 Token 上下文时返回 null，
            // 由调用方按“未登录”处理，而不是把上下文异常抛成 500。
            return null;
        }
    }

    public static String getUsername() {
        return getSessionString(SESSION_USERNAME);
    }

    public static String getNickname() {
        return getSessionString(SESSION_NICKNAME);
    }

    public static void setLoginUser(Long memberId, String username, String nickname, Long levelId) {
        SaSession session = StpMemberUtil.getSession();
        session.set(SESSION_MEMBER_ID, memberId);
        session.set(SESSION_USERNAME, username);
        session.set(SESSION_NICKNAME, nickname);
        session.set(SESSION_LEVEL_ID, levelId);
    }

    /** 刷新昵称 / 等级等展示信息，不改登录态。 */
    public static void refreshProfile(String nickname, Long levelId) {
        SaSession session = StpMemberUtil.getSession();
        if (session == null) {
            return;
        }
        session.set(SESSION_NICKNAME, nickname);
        session.set(SESSION_LEVEL_ID, levelId);
    }

    public static void setLoginContext(String loginIp, String userAgent) {
        SaSession tokenSession = StpMemberUtil.getTokenSession();
        if (tokenSession == null) {
            return;
        }
        tokenSession.set(SESSION_LOGIN_IP, loginIp);
        tokenSession.set(SESSION_USER_AGENT, userAgent);
    }

    private static String getSessionString(String key) {
        try {
            SaSession session = StpMemberUtil.getSession();
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
