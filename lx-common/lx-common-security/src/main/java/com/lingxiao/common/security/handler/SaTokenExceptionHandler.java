package com.lingxiao.common.security.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.core.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 鉴权异常处理。
 *
 * <p>必须比 {@code GlobalExceptionHandler} 优先，否则会被通用 Exception 分支吃掉，
 * 前端只能看到 9000，无法区分“没登录”和“没权限”。</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.debug("未登录访问: {}", e.getMessage());
        return R.fail(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleNoPermission(Exception e) {
        log.warn("越权访问: {}", e.getMessage());
        return R.fail(ErrorCode.FORBIDDEN);
    }
}
