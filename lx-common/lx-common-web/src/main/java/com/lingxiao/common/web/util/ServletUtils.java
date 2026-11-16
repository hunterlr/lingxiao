package com.lingxiao.common.web.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 请求上下文工具。
 *
 * <p>只读，不修改请求；拿不到请求时返回安全默认值，避免在定时任务 / 异步线程里抛 NPE。</p>
 */
public final class ServletUtils {

    private ServletUtils() {
    }

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    /**
     * 获取客户端 IP。
     *
     * <p>代理场景按 X-Forwarded-For 第一个非 unknown 地址取；生产必须由 Nginx 覆盖该头，
     * 否则客户端可以伪造。内网直连时回退 remoteAddr。</p>
     */
    public static String getClientIp() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (isValid(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (isValid(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    public static String getUserAgent() {
        HttpServletRequest request = getRequest();
        return request == null ? "" : String.valueOf(request.getHeader("User-Agent"));
    }

    private static boolean isValid(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}
