package com.lingxiao.modules.generator.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 代码生成器的固定取值。
 *
 * <p>集中放常量而不是散落在 Service 里：前端下拉、后端校验、模板判断
 * 必须用同一份集合，否则“页面能选、后端拒绝”这种问题很难查。</p>
 */
public final class GenConstants {

    private GenConstants() {
    }

    /** 支持的单表生成类型。树表 / 主子表在 v1.0 不生成。 */
    public static final String GEN_TYPE_SINGLE = "0";

    /** 默认模板组。v1.0 只维护一套模板。 */
    public static final String TEMPLATE_GROUP_DEFAULT = "default";

    public static final String YES = "1";
    public static final String NO = "0";

    /** 查询方式：v1.0 不做 BETWEEN，避免生成“两值参数 + 边界处理”的复杂代码。 */
    public static final Set<String> QUERY_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("EQ", "LIKE", "GT", "GE", "LT", "LE")));

    /** 表单控件类型，与 docs/12-api-codegen.md 的清单一致。 */
    public static final Set<String> HTML_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "input", "textarea", "select", "radio", "checkbox",
                    "datetime", "upload", "editor")));

    /** 生成实体允许的 Java 类型；白名单之外的列导入时回退为 String。 */
    public static final Set<String> JAVA_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "String", "Long", "Integer", "BigDecimal", "Double", "Float",
                    "Boolean", "LocalDate", "LocalDateTime", "LocalTime", "byte[]")));

    /** 主键允许的 Java 类型：Controller 路径参数、Service 入参都用它。 */
    public static final Set<String> PK_JAVA_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("Long", "Integer", "String")));

    /** 表名 / 列名只允许字母开头，防止把反引号、空格拼进 SQL 或模板。 */
    public static final String IDENTIFIER_REGEX = "^[A-Za-z][A-Za-z0-9_]{0,63}$";

    /** 实体类名：PascalCase，允许数字但不能开头。 */
    public static final String CLASS_NAME_REGEX = "^[A-Z][A-Za-z0-9]{0,63}$";

    /** 模块名：小写字母开头，用于包名与 URL 段。 */
    public static final String MODULE_NAME_REGEX = "^[a-z][a-z0-9]{0,31}$";

    /** 业务名：camelCase。 */
    public static final String BUSINESS_NAME_REGEX = "^[a-z][A-Za-z0-9]{0,63}$";

    /** Java 字段名。 */
    public static final String JAVA_FIELD_REGEX = "^[a-z][A-Za-z0-9]{0,63}$";

    /** 前端路由：只允许一段段普通字符，禁止 .. 与反斜杠。 */
    public static final String GEN_PATH_REGEX = "^/[A-Za-z0-9_\\-/]{1,200}$";
}
