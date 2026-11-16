package com.lingxiao.common.security.util;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;

import java.util.List;

/**
 * 平台后台安全域。
 *
 * <p><b>为什么不用 StpUtil：</b>StpUtil 是 Sa-Token 的默认域，登录类型为 {@code login}。
 * 凌霄要求后台 / 租户 / 会员三套 Token 互不认，必须各自持有一个独立的 {@link StpLogic}。
 * 本类就是平台域的入口，后续 tenant / member 会各自新增一个同构类。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>登录类型固定为 {@code platform}，Redis Key 前缀、Session 命名空间都会带上它；</li>
 *   <li>所有后台 Controller 只允许通过本类取登录态，禁止直接使用 StpUtil；</li>
 *   <li>平台 Token 拿到租户 / 会员入口必须直接 401，入口隔离还要在 Nginx 层再做一次。</li>
 * </ul>
 */
public final class StpPlatformUtil {

    /** 平台安全域标识；修改它等于让所有已发 Token 失效 */
    public static final String TYPE = "platform";

    /** 平台域独立的 StpLogic 实例 */
    private static final StpLogic STP_LOGIC = new StpLogic(TYPE);

    private StpPlatformUtil() {
    }

    public static void login(Object loginId) {
        STP_LOGIC.login(loginId);
    }

    public static void logout() {
        STP_LOGIC.logout();
    }

    /**
     * 踢掉指定账号的<b>全部</b>会话（所有设备、所有 Token）。
     *
     * <p>用于改密、停用账号等“必须让旧凭证立即失效”的场景：
     * {@code logout()} 只注销当前 Token，其它端仍然能用；这里按 loginId 注销账号级 Session，
     * 所有 Token 一并失效。</p>
     */
    public static void logoutByUserId(Object loginId) {
        STP_LOGIC.logout(loginId);
    }

    public static String getTokenValue() {
        return STP_LOGIC.getTokenValue();
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

    /**
     * 校验当前登录用户是否拥有某个权限码，没有则抛 {@code NotPermissionException}。
     *
     * <p>权限来源是 {@code StpInterfaceImpl}（用户 → 角色 → 菜单 perms）。
     * 超级管理员返回 {@code *:*:*}，由 Sa-Token 的通配匹配放行。</p>
     */
    public static void checkPermission(String permission) {
        STP_LOGIC.checkPermission(permission);
    }

    public static boolean hasPermission(String permission) {
        return STP_LOGIC.hasPermission(permission);
    }

    public static void checkRole(String roleKey) {
        STP_LOGIC.checkRole(roleKey);
    }

    public static SaSession getSession() {
        return STP_LOGIC.getSession();
    }

    public static long getTokenTimeout() {
        return STP_LOGIC.getTokenTimeout();
    }

    // ==================== 在线会话管理（W4 系统监控） ====================
    //
    // 在线用户页需要“枚举所有平台会话 → 读取会话信息 → 强制下线”。
    // 这些能力都来自 Sa-Token 的 StpLogic，但把 token 值、Key 前缀等细节
    // 收在本类里，Controller / Service 不直接碰 StpLogic，也不自己拼 Redis Key。
    // 后续 tenant / member 域要复用时，照抄本类即可，不会串域。

    /**
     * 枚举平台域已签发的 Token。
     *
     * <p>{@code start=0, size=-1} 表示取全部；分页在服务层按“过滤后的会话”做，
     * 避免先分页再过滤导致总数错乱。</p>
     *
     * @param keyword 按 token 值模糊匹配，传空串表示不过滤
     * @return 带 Key 前缀的完整 Token Key（例如 {@code lx-platform-token:platform:token:xxx}）
     */
    public static List<String> searchTokenValue(String keyword, int start, int size) {
        return STP_LOGIC.searchTokenValue(keyword, start, size, false);
    }

    /**
     * 平台域 Token Key 前缀。
     *
     * <p>{@link #searchTokenValue} 返回的是完整 Key，拿去调
     * {@code getLoginIdByToken} 之类的方法前必须把前缀切掉。</p>
     */
    public static String getTokenKeyPrefix() {
        return STP_LOGIC.splicingKeyTokenValue("");
    }

    /** 按 Token 值取登录用户 ID；Token 不存在 / 已过期返回 {@code null}。 */
    public static Object getLoginIdByToken(String tokenValue) {
        return STP_LOGIC.getLoginIdByToken(tokenValue);
    }

    /**
     * 按 Token 值取 Token-Session。
     *
     * <p>第二个参数传 {@code false}：不存在时返回 {@code null}，不新建空 Session，
     * 避免监控列表把不存在的 Token 也“创建”出来。</p>
     */
    public static SaSession getTokenSession(String tokenValue) {
        return STP_LOGIC.getTokenSessionByToken(tokenValue, false);
    }

    /** 当前请求 Token 对应的 Token-Session（不存在返回 null）。 */
    public static SaSession getTokenSession() {
        return STP_LOGIC.getTokenSession();
    }

    /** 当前请求的完整 Token 值（未截断，带前缀），仅用于内部比对。 */
    public static String getTokenValueNotCut() {
        return STP_LOGIC.getTokenValueNotCut();
    }

    /** 指定 Token 的剩余有效期（秒）；已失效返回 -2。 */
    public static long getTokenTimeout(String tokenValue) {
        return STP_LOGIC.getTokenTimeout(tokenValue);
    }

    /**
     * 指定 Token 的最后活动时间（毫秒时间戳）。
     *
     * <p>Sa-Token 只有在开启 {@code active-timeout} 时才会在登录时写入这个 Key；
     * 未开启时返回 0，由调用方回退到 Session 创建时间。这里不主动补写，
     * 否则“最后活动”会变成“监控接口被访问的时间”。</p>
     */
    public static long getTokenLastActiveTime(String tokenValue) {
        try {
            SaTokenDao dao = STP_LOGIC.getSaTokenDao();
            String value = dao.get(STP_LOGIC.splicingKeyLastActiveTime(tokenValue));
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            // 缓存实现差异 / 脏数据都不应该让监控页 500
            return 0L;
        }
    }

    /** 按 Token 值强制下线（删除 Token 及其 Session）。 */
    public static void logoutByTokenValue(String tokenValue) {
        STP_LOGIC.logoutByTokenValue(tokenValue);
    }
}
