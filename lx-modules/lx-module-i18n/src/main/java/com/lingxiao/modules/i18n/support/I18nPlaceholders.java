package com.lingxiao.modules.i18n.support;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文案占位符工具。
 *
 * <p>只认 Spring {@code MessageFormat} 风格：{@code {0}}、{@code {1}}。
 * 发布前校验默认文案与译文的占位符集合必须一致，
 * 否则英文文案会丢掉数字 / 账号等动态参数。</p>
 */
public final class I18nPlaceholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private I18nPlaceholders() {
    }

    public static Set<Integer> of(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> result = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            result.add(Integer.valueOf(matcher.group(1)));
        }
        return result;
    }

    /** 占位符集合一致（忽略顺序）。 */
    public static boolean sameAs(String expected, String actual) {
        return of(expected).equals(of(actual));
    }
}
