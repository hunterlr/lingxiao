package com.lingxiao.common.core.exception;

import com.lingxiao.common.core.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>只用于“可预期”的错误：参数不对、账号停用、文件超限等。
 * 不要用它包裹 SQL 异常、空指针等系统错误；那些交给全局异常处理器记录并返回 9000。</p>
 */
@Getter
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public ServiceException(String message) {
        super(message);
        this.code = ErrorCode.FAIL.getCode();
    }

    public ServiceException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public ServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
