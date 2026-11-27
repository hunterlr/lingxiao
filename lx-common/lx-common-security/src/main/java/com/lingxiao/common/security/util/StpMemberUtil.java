package com.lingxiao.common.security.util;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;

/**
 * 前台会员安全域。
 *
 * <p>与 {@link StpPlatformUtil} 完全独立：独立的 loginType、独立的 Token 名
 * （{@code lx-member-token}）、独立的 Session 命名空间。平台 Token 拿到
 * {@code /api/app/**} 不会被会员拦截器识别，会员 Token 拿到
 * {@code /api/admin/**} 也不会被平台拦截器识别。</p>
 *
 * <p><b>Token 配置：</b>会员模块启用时由 {@code MemberSaTokenConfigure} 在启动阶段
 * 复制全局 Sa-Token 配置，再覆盖 Token 名、有效期和并发策略；本类只负责
 * 把配置挂到自己的 {@link StpLogic} 上，不修改全局配置。</p>
 *
 * <p>账号级注销统一用 {@link #logoutByUserId(Object)}：改密、停用、管理员踢人时，
 * 该会员所有设备上的 Token 一起失效。</p>
 */
public final class StpMemberUtil {

    /** 会员安全域标识；修改它等于让所有已发会员 Token 失效 */
    public static final String TYPE = "member";

    /** 会员 Token 请求头名；前端会员端必须使用它 */
    public static final String TOKEN_NAME = "lx-member-token";

    /** 会员域独立的 StpLogic 实例 */
    private static final StpLogic STP_LOGIC = new StpLogic(TYPE);

    private StpMemberUtil() {
    }

    /**
     * 挂载会员域独立配置。
     *
     * <p>必须在对外提供请求之前调用（由配置类在启动阶段完成），否则会回退到
     * 全局配置的 Token 名（{@code lx-platform-token}），导致后台与会员 Token 串域。</p>
     */
    public static void setConfig(SaTokenConfig config) {
        STP_LOGIC.setConfig(config);
    }

    public static void login(Object loginId) {
        STP_LOGIC.login(loginId);
    }

    public static void logout() {
        STP_LOGIC.logout();
    }

    /** 踢掉指定会员的全部会话（所有设备、所有 Token）。 */
    public static void logoutByUserId(Object loginId) {
        STP_LOGIC.logout(loginId);
    }

    public static String getTokenValue() {
        return STP_LOGIC.getTokenValue();
    }

    public static String getTokenName() {
        return STP_LOGIC.getTokenName();
    }

    public static Object getLoginIdDefaultNull() {
        return STP_LOGIC.getLoginIdDefaultNull();
    }

    public static String getLoginIdAsString() {
        Object id = STP_LOGIC.getLoginIdDefaultNull();
        return id == null ? null : String.valueOf(id);
    }

    public static void checkLogin() {
        STP_LOGIC.checkLogin();
    }

    public static boolean isLogin() {
        return STP_LOGIC.isLogin();
    }

    public static long getTokenTimeout() {
        return STP_LOGIC.getTokenTimeout();
    }

    public static SaSession getSession() {
        return STP_LOGIC.getSession();
    }

    public static SaSession getTokenSession() {
        return STP_LOGIC.getTokenSession();
    }

    /**
     * 指定会员当前仍然有效的 Token 列表。
     *
     * <p>多端登录开启时可能有多条；会员中心“登录设备”用它枚举会话。
     * 只返回 Token 值，业务层不要把它直接下发前端，用哈希做句柄。</p>
     */
    public static java.util.List<String> getTokenValueListByLoginId(Object loginId) {
        return STP_LOGIC.getTokenValueListByLoginId(loginId);
    }

    /** 指定 Token 的剩余有效期（秒）；已失效返回 -2（Sa-Token 约定），未找到为 -1。 */
    public static long getTokenTimeout(String tokenValue) {
        return STP_LOGIC.getTokenTimeout(tokenValue);
    }

    /** 读取指定 Token 的 Token-Session；不创建，避免枚举会话时凭空造 Session。 */
    public static SaSession getTokenSessionByToken(String tokenValue) {
        return STP_LOGIC.getTokenSessionByToken(tokenValue, false);
    }

    /** 踢掉指定 Token 对应的单个会话，不影响同账号其它设备。 */
    public static void logoutByTokenValue(String tokenValue) {
        STP_LOGIC.logoutByTokenValue(tokenValue);
    }
}
