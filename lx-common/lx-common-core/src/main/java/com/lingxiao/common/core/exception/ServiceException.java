package com.lingxiao.common.core.exception;

import com.lingxiao.common.core.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>只用于“可预期”的错误：参数不对、账号停用、文件超限等。
 * 不要用它包裹 SQL 异常、空指针等系统错误；那些交给全局异常处理器记录并返回 9000。</p>
 *
 * <p>多语言约定（见 docs/23）：</p>
 * <ul>
 *   <li>{@code new ServiceException(ErrorCode.X)}：自动带 {@code messageKey = "error.<code>"}，
 *       由全局异常处理器按请求语言翻译，{@code getMessage()} 仍是枚举里的中文兜底；</li>
 *   <li>{@code new ServiceException(ErrorCode.X, "中文字面量")}：保留原文，不翻译。
 *       适合“带业务数据拼接”的临时提示，后续应逐步换成下面的带参形式；</li>
 *   <li>{@code new ServiceException(ErrorCode.X, "error.2004.locked", 10)}：带参翻译，
 *       {@code {0}} 会被 {@code 10} 替换。</li>
 * </ul>
 */
@Getter
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    /** 语言包键，可空；非空时由全局异常处理器翻译成 msg */
    private final String messageKey;

    /** 语言包占位参数，可空 */
    private final Object[] args;

    public ServiceException(String message) {
        super(message);
        this.code = ErrorCode.FAIL.getCode();
        this.messageKey = null;
        this.args = null;
    }

    public ServiceException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
        this.messageKey = "error." + errorCode.getCode();
        this.args = null;
    }

    public ServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.messageKey = null;
        this.args = null;
    }

    /**
     * 带参翻译构造。
     *
     * <p>{@code new ServiceException(ErrorCode.X, "a")} 仍走上面的两参构造（字面量），
     * 只有三个及以上参数才会落到这里，避免把“字面量”误判成“语言包键”。</p>
     *
     * <p>{@code getMessage()} 保留枚举里的中文兜底，而不是暴露 key：
     * 万一线程没有请求上下文（定时任务、异步）导致翻译失败，
     * 用户看到的也是“账号已被锁定，请稍后再试”，而不是 {@code error.2004.locked}。</p>
     */
    public ServiceException(ErrorCode errorCode, String messageKey, Object... args) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
        this.messageKey = messageKey;
        this.args = args;
    }

    /** 是否带语言包键（决定要不要走 MessageSource） */
    public boolean hasMessageKey() {
        return messageKey != null && !messageKey.isEmpty();
    }

    /**
     * 带语言包键的业务异常（无占位参数时用这个，语义比三参构造更清楚）。
     *
     * <pre>{@code
     * throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.nickname");
     * }</pre>
     */
    public static ServiceException keyed(ErrorCode errorCode, String messageKey, Object... args) {
        return new ServiceException(errorCode, messageKey, args);
    }
}
