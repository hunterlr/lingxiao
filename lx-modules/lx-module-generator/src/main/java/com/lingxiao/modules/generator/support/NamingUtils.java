package com.lingxiao.modules.generator.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 表名 / 列名到 Java 命名的转换。
 *
 * <p>规则刻意保守：</p>
 * <ul>
 *   <li>{@code sys_user} → {@code SysUser} / {@code sysUser}；</li>
 *   <li>{@code create_time} → {@code createTime}；</li>
 *   <li>连续下划线、数字段都能处理，但不会猜测缩写（{@code dept_id} 不会变 {@code deptId} 之外的东西）；</li>
 *   <li>结果是 Java 关键字时追加 {@code Field}，例如 {@code class} → {@code classField}。</li>
 * </ul>
 *
 * <p>不做“表前缀剥离”：前缀规则因项目而异，自动剥离反而让生成结果不可预测。
 * 需要去掉 {@code sys_} 时，在页面上改实体类名即可。</p>
 */
public final class NamingUtils {

    private NamingUtils() {
    }

    /** Java 关键字与字面量：不能直接当字段名。 */
    private static final Set<String> JAVA_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var", "record", "yield", "sealed", "permits")));

    /** 表名 / 列名 → PascalCase；非法输入返回空串，由调用方给出业务错误。 */
    public static String toPascalCase(String name) {
        String camel = toCamelCase(name);
        if (camel.isEmpty()) {
            return camel;
        }
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    /** 表名 / 列名 → camelCase。 */
    public static String toCamelCase(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        String[] parts = name.trim().toLowerCase().split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(part);
            } else {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /** 关键字避让：{@code class} → {@code classField}。 */
    public static String safeJavaField(String field) {
        if (field == null || field.isEmpty()) {
            return field;
        }
        return JAVA_KEYWORDS.contains(field) ? field + "Field" : field;
    }

    /** 字段名首字母大写，用于 getter / setter 拼接。 */
    public static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
