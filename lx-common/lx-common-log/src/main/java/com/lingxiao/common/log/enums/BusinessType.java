package com.lingxiao.common.log.enums;

/**
 * 操作类型。
 *
 * <p>与前端日志筛选、后端统计口径保持一致；新增类型只追加，不改已有值。</p>
 */
public enum BusinessType {

    OTHER,
    INSERT,
    UPDATE,
    DELETE,
    GRANT,
    EXPORT,
    IMPORT,
    FORCE_LOGOUT,
    CLEAN
}
