package com.lingxiao.common.log.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.domain.OperLogRecord;
import com.lingxiao.common.log.store.OperLogStore;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.common.web.util.ServletUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 操作日志切面。
 *
 * <p>采集“谁、什么时候、调了什么、耗时多少、成功失败”，然后交给
 * {@link OperLogStore} 落库。切面本身不依赖系统模块：没有存储实现时
 * 只打应用日志，保证模块可裁剪。</p>
 *
 * <p><b>约束：</b></p>
 * <ul>
 *   <li>日志写入失败只 warn，绝不影响主业务；</li>
 *   <li>请求参数里的密码 / Token / Secret 必须脱敏；</li>
 *   <li>参数与结果截断到 2000 字符，避免大 JSON 撑爆日志表；</li>
 *   <li>文件上传不记录二进制内容，{@link MultipartFile} 参数直接跳过。</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    /** 单字段最大长度，和 sys_oper_log 的 text 字段相比留足余量 */
    private static final int MAX_TEXT = 2000;

    /** 匹配 “password”: “xxx” 这类 JSON 字段，值替换为 *** */
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(\"[^\"]*(?:password|passwd|secret|token)[^\"]*\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final ObjectProvider<OperLogStore> storeProvider;

    public LogAspect(ObjectMapper objectMapper, ObjectProvider<OperLogStore> storeProvider) {
        this.objectMapper = objectMapper;
        this.storeProvider = storeProvider;
    }

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long start = System.currentTimeMillis();
        OperLogStore store = storeProvider.getIfAvailable();
        String username = safeUsername();
        String ip = ServletUtils.getClientIp();
        String method = joinPoint.getSignature().toShortString();
        HttpServletRequest request = ServletUtils.getRequest();
        String requestMethod = request == null ? null : request.getMethod();
        String url = request == null ? null : request.getRequestURI();
        // 没有存储实现时不做序列化，避免无谓开销
        String params = store != null && logAnnotation.saveRequestData()
                ? toJson(filterArgs(joinPoint.getArgs()))
                : null;

        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            log.info("[操作日志] title={}, type={}, user={}, ip={}, method={}, cost={}ms, status=OK",
                    logAnnotation.title(), logAnnotation.businessType(), username, ip, method, cost);
            save(store, logAnnotation, username, ip, method, requestMethod, url,
                    params, "0", null, toJson(result), cost);
            return result;
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[操作日志] title={}, type={}, user={}, ip={}, method={}, cost={}ms, status=FAIL, error={}",
                    logAnnotation.title(), logAnnotation.businessType(), username, ip, method, cost, e.getMessage());
            save(store, logAnnotation, username, ip, method, requestMethod, url,
                    params, "1", e.getMessage(), null, cost);
            throw e;
        }
    }

    private void save(OperLogStore store, Log logAnnotation, String username, String ip,
                      String method, String requestMethod, String url, String params,
                      String status, String errorMsg, String result, long cost) {
        if (store == null) {
            return;
        }
        try {
            store.save(OperLogRecord.builder()
                    .title(logAnnotation.title())
                    .businessType(logAnnotation.businessType().ordinal())
                    .method(limit(method, 255))
                    .requestMethod(requestMethod)
                    .operName(username)
                    .operUrl(limit(url, 500))
                    .operIp(ip)
                    .operParam(params)
                    .jsonResult(limit(result, MAX_TEXT))
                    .status(status)
                    .errorMsg(limit(errorMsg, MAX_TEXT))
                    .operTime(LocalDateTime.now())
                    .costTime(cost)
                    .build());
        } catch (Exception e) {
            log.warn("[操作日志] 落库失败，不影响业务: title={}, error={}", logAnnotation.title(), e.getMessage());
        }
    }

    /** 过滤掉 Servlet 对象、文件与绑定结果，只保留可序列化的业务参数 */
    private Object filterArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> kept = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null || arg instanceof ServletRequest || arg instanceof ServletResponse
                    || arg instanceof MultipartFile || arg instanceof BindingResult
                    || arg instanceof byte[]) {
                continue;
            }
            kept.add(arg);
        }
        if (kept.isEmpty()) {
            return null;
        }
        return kept.size() == 1 ? kept.get(0) : kept;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return SENSITIVE_JSON.matcher(json).replaceAll("$1\"***\"");
        } catch (Exception e) {
            // 序列化失败时退化为 toString，仍做长度限制
            return limit(String.valueOf(value), MAX_TEXT);
        }
    }

    private String limit(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(truncated)";
    }

    private String safeUsername() {
        try {
            String username = LoginHelper.getUsername();
            return username == null ? "anonymous" : username;
        } catch (Exception ignored) {
            return "anonymous";
        }
    }
}
