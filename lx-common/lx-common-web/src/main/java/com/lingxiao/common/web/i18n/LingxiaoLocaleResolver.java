package com.lingxiao.common.web.i18n;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 请求语言解析。
 *
 * <p>优先级（与 docs/23 一致）：</p>
 * <ol>
 *   <li>{@code X-Lang} 请求头 —— 前端自己记着用户选的语言，最可靠；</li>
 *   <li>{@code ?lang=} 参数 —— 方便用浏览器 / curl 临时验证；</li>
 *   <li>{@code Accept-Language} —— 浏览器默认值；</li>
 *   <li>兜底 {@code zh-CN}。</li>
 * </ol>
 *
 * <p>只认 {@code zh-CN} 与 {@code en-US} 两种；{@code zh}、{@code zh_CN}、{@code en}、
 * {@code en-GB} 这类写法统一归一化，避免语言包找不到而回退成中文。</p>
 *
 * <p>无状态实现：语言只存在本次请求里，不写 ThreadLocal，
 * 线程池复用时不会串味。</p>
 */
public class LingxiaoLocaleResolver implements org.springframework.web.servlet.LocaleResolver {

    /** 前端请求头，与 {@code lx-platform-token} 并列。 */
    public static final String LANG_HEADER = "X-Lang";

    /** 查询参数，便于调试。 */
    public static final String LANG_PARAM = "lang";

    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    /** 支持的语言标签，顺序无关。 */
    public static final List<String> SUPPORTED_LANGUAGES = java.util.Arrays.asList("zh-CN", "en-US");

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Locale locale = parse(request.getHeader(LANG_HEADER));
        if (locale == null) {
            locale = parse(request.getParameter(LANG_PARAM));
        }
        if (locale == null) {
            locale = fromAcceptLanguage(request);
        }
        return locale == null ? DEFAULT_LOCALE : locale;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // 无状态：语言由每个请求自己带，不需要写 Cookie / Session
    }

    /**
     * 解析语言标签。
     *
     * @return 不认识时返回 null，交给下一级判断
     */
    static Locale parse(String value) {
        if (value == null) {
            return null;
        }
        String tag = value.trim().replace('_', '-');
        if (tag.isEmpty()) {
            return null;
        }
        String lower = tag.toLowerCase(Locale.ROOT);
        if (lower.startsWith("zh")) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if (lower.startsWith("en")) {
            return Locale.US;
        }
        return null;
    }

    private Locale fromAcceptLanguage(HttpServletRequest request) {
        Enumeration<Locale> locales = request.getLocales();
        while (locales != null && locales.hasMoreElements()) {
            Locale candidate = locales.nextElement();
            Locale matched = parse(candidate.getLanguage());
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }
}
