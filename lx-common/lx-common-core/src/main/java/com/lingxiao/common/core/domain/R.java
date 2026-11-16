package com.lingxiao.common.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.i18n.MessageUtils;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>所有 Controller 都返回 {@code R<T>}，前端只需判断 {@code code == 0}。
 * 不要在这里放分页字段：分页用 {@code PageResult<T>}，避免响应体膨胀。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，0 表示成功；非 0 见 {@link ErrorCode} */
    private int code;

    /** 给用户看的提示，不包含堆栈、SQL 等敏感信息 */
    private String msg;

    /**
     * 提示文案的语言包键，例如 {@code error.2001}。
     *
     * <p>可空。{@code msg} 已经是按请求语言翻译好的文案，前端直接弹即可；
     * 这个字段留给“前端想自己二次翻译 / 做埋点”的场景，旧前端不认识它会忽略。
     * 为 null 时不输出，避免成功响应里多一个 {@code "messageKey": null}。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String messageKey;

    /** 业务数据，失败时通常为 null */
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.SUCCESS.getCode();
        // 成功提示也走语言包；没接语言包时（单测、非 Web 场景）退回枚举里的中文
        r.msg = MessageUtils.message(MessageUtils.errorKey(r.code), ErrorCode.SUCCESS.getMsg());
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(String msg) {
        return fail(ErrorCode.FAIL.getCode(), msg);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        String key = MessageUtils.errorKey(errorCode.getCode());
        return fail(errorCode.getCode(), MessageUtils.message(key, errorCode.getMsg()), key);
    }

    public static <T> R<T> fail(int code, String msg) {
        return fail(code, msg, null);
    }

    /**
     * 带语言包键的失败响应。
     *
     * @param code       稳定错误码，前端逻辑判断只看它
     * @param msg        已按请求语言翻译好的文案
     * @param messageKey 语言包键，可空
     */
    public static <T> R<T> fail(int code, String msg, String messageKey) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        r.messageKey = messageKey;
        return r;
    }

    /**
     * 是否成功。
     *
     * <p>加 {@code @JsonIgnore}：这是给后端代码判断用的方法，不应出现在响应体里，
     * 否则前端会看到多余的 {@code "success": true} 字段。</p>
     */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
