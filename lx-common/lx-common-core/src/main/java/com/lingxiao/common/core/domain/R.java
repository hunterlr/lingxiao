package com.lingxiao.common.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lingxiao.common.core.enums.ErrorCode;
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

    /** 业务数据，失败时通常为 null */
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.msg = ErrorCode.SUCCESS.getMsg();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(String msg) {
        return fail(ErrorCode.FAIL.getCode(), msg);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMsg());
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
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
