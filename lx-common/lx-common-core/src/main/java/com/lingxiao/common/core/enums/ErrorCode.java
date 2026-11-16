package com.lingxiao.common.core.enums;

import lombok.Getter;

/**
 * 全局错误码。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>0：成功；</li>
 *   <li>1xxx：通用 / 参数 / 权限；</li>
 *   <li>2xxx：认证与账号；</li>
 *   <li>3xxx：文件；</li>
 *   <li>9xxx：系统内部错误。</li>
 * </ul>
 *
 * <p>错误码只增不改；前端按 code 做分支，不要依赖 msg 文案。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "操作成功"),
    FAIL(1000, "操作失败"),
    BAD_REQUEST(1001, "请求参数不正确"),
    UNAUTHORIZED(1002, "未登录或登录已过期"),
    FORBIDDEN(1003, "没有权限执行该操作"),
    NOT_FOUND(1004, "资源不存在"),
    METHOD_NOT_ALLOWED(1005, "请求方法不支持"),
    TOO_MANY_REQUESTS(1006, "请求过于频繁，请稍后再试"),

    LOGIN_FAILED(2001, "用户名或密码错误"),
    ACCOUNT_DISABLED(2002, "账号已被停用"),
    CAPTCHA_INVALID(2003, "验证码错误或已过期"),
    ACCOUNT_LOCKED(2004, "账号已被锁定，请稍后再试"),
    OLD_PASSWORD_ERROR(2005, "原密码不正确"),

    FILE_UPLOAD_FAILED(3001, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(3002, "文件类型不允许"),
    FILE_TOO_LARGE(3003, "文件超过大小限制"),
    FILE_NOT_FOUND(3004, "文件不存在"),

    INTERNAL_ERROR(9000, "系统内部错误");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
