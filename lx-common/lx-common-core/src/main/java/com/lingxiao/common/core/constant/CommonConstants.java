package com.lingxiao.common.core.constant;

/**
 * 全局常量。
 *
 * <p>只放“跨模块都要用且不会频繁变化”的值；业务枚举请放各自模块。</p>
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 通用启用 / 停用状态：0 正常，1 停用（与字典 sys_common_status 对齐） */
    public static final String STATUS_NORMAL = "0";
    public static final String STATUS_DISABLED = "1";

    /** 逻辑删除：0 未删除，1 已删除 */
    public static final String DEL_FLAG_NORMAL = "0";
    public static final String DEL_FLAG_DELETED = "1";

    /** 是 / 否 */
    public static final String YES = "Y";
    public static final String NO = "N";

    /** 超级管理员用户 ID，拥有全部权限的兜底判断 */
    public static final Long SUPER_ADMIN_ID = 1L;

    /** 内置超级管理员角色 ID，不允许删除 / 停用 / 改标识 */
    public static final Long SUPER_ADMIN_ROLE_ID = 1L;

    /** 平台后台安全域标识，对应 StpPlatformUtil */
    public static final String REALM_PLATFORM = "platform";
    public static final String REALM_TENANT = "tenant";
    public static final String REALM_MEMBER = "member";
}
