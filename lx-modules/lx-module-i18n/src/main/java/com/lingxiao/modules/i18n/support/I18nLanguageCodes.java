package com.lingxiao.modules.i18n.support;

import java.util.Locale;

/**
 * 语言标签换算。
 *
 * <p>请求侧 {@code Locale} 可能是 {@code zh_CN} / {@code en} / {@code en-GB}，
 * 而数据库统一存 BCP 47（{@code zh-CN} / {@code en-US}）。这里做一次收敛：</p>
 * <ul>
 *   <li>中文一律落到 {@code zh-CN}；英文一律落到 {@code en-US}；</li>
 *   <li>其他语言保留 {@code language-REGION}，没有地区就用语言本身；</li>
 *   <li>这样后续在后台新增语言时，不用改 MessageSource。</li>
 * </ul>
 */
public final class I18nLanguageCodes {

    private I18nLanguageCodes() {
    }

    /** 默认语言：与 LingxiaoLocaleResolver 保持一致。 */
    public static final String DEFAULT = "zh-CN";

    public static String fromLocale(Locale locale) {
        if (locale == null) {
            return DEFAULT;
        }
        String language = locale.getLanguage();
        if (language == null || language.isEmpty()) {
            return DEFAULT;
        }
        if ("zh".equalsIgnoreCase(language)) {
            return "zh-CN";
        }
        if ("en".equalsIgnoreCase(language)) {
            return "en-US";
        }
        String country = locale.getCountry();
        return country == null || country.isEmpty()
                ? language.toLowerCase(Locale.ROOT)
                : language.toLowerCase(Locale.ROOT) + "-" + country.toUpperCase(Locale.ROOT);
    }

    /**
     * 语言标签归一：请求参数可能写成 {@code zh_CN} / {@code zh-cn}。
     */
    public static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String tag = code.trim().replace('_', '-');
        if (tag.isEmpty()) {
            return null;
        }
        String lower = tag.toLowerCase(Locale.ROOT);
        if ("zh".equals(lower) || lower.startsWith("zh-")) {
            return "zh-CN";
        }
        if ("en".equals(lower) || lower.startsWith("en-")) {
            return "en-US";
        }
        String[] parts = tag.split("-");
        if (parts.length == 1) {
            return parts[0].toLowerCase(Locale.ROOT);
        }
        return parts[0].toLowerCase(Locale.ROOT) + "-" + parts[1].toUpperCase(Locale.ROOT);
    }

    /** 从完整键取命名空间，例如 {@code error.2001} -> {@code error}。 */
    public static String namespaceOf(String keyCode) {
        if (keyCode == null) {
            return "";
        }
        int dot = keyCode.indexOf('.');
        return dot > 0 ? keyCode.substring(0, dot) : keyCode;
    }
}
