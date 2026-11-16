package com.lingxiao.common.web.handler;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.core.i18n.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>目标：前端永远拿到结构一致的 {@link R}，不把堆栈、SQL、类名暴露给用户。</p>
 *
 * <p>处理顺序：</p>
 * <ol>
 *   <li>业务异常：按异常里的 code / messageKey 返回，带 key 的先按请求语言翻译；</li>
 *   <li>参数校验：拼接字段错误，返回 1001；</li>
 *   <li>请求方法不支持：405；</li>
 *   <li>其他：记录 error 日志 + traceId，返回 9000。</li>
 * </ol>
 *
 * <p>多语言说明：{@code ServiceException} 只有带 {@code messageKey} 时才翻译，
 * 字面量消息原样返回——那些通常是拼接了业务数据的临时提示，硬翻反而容易出错。
 * 校验消息由 Hibernate Validator 在绑定阶段就用同一个 MessageSource 解析，
 * 到这里已经是目标语言了。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e) {
        String key = e.hasMessageKey() ? e.getMessageKey() : null;
        String msg = e.getMessage();
        if (key != null) {
            msg = messageSource.getMessage(key, e.getArgs(), msg, LocaleContextHolder.getLocale());
        }
        log.warn("业务异常: code={}, key={}, msg={}", e.getCode(), key, msg);
        return R.fail(e.getCode(), msg, key);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(listSeparator()));
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(listSeparator()));
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(listSeparator()));
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        String key = "error.1005.method";
        String msg = messageSource.getMessage(key, new Object[]{e.getMethod()},
                "不支持的请求方法: " + e.getMethod(), LocaleContextHolder.getLocale());
        return R.fail(ErrorCode.METHOD_NOT_ALLOWED.getCode(), msg, key);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        // 系统异常必须留完整堆栈给运维，但不能返回给前端
        log.error("系统异常", e);
        return R.fail(ErrorCode.INTERNAL_ERROR);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    /** 多条校验错误之间的分隔符：中文用全角分号，英文用分号 + 空格。 */
    private String listSeparator() {
        return MessageUtils.message("common.listSeparator", "；");
    }
}
