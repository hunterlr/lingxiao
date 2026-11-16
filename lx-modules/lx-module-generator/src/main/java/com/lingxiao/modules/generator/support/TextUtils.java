package com.lingxiao.modules.generator.support;

/**
 * 注释 / 标签文本清理。
 *
 * <p>数据库注释会同时进入 Java 注释、Vue 模板和 JS 字符串，必须在这里统一处理：</p>
 * <ul>
 *   <li>换行、制表符 → 空格：避免把模板结构打断；</li>
 *   <li>{@code *&#47;} → {@code * /}：否则会提前结束 Javadoc；</li>
 *   <li>空白折叠 + 长度截断：页面标签不需要保留排版。</li>
 * </ul>
 *
 * <p>这里不做 HTML / SQL 转义：模板里只把注释放在文本节点和受控字符串中，
 * 菜单 SQL 只使用表名 / 类名等经过正则校验的标识符，不使用自由文本。</p>
 */
public final class TextUtils {

    private TextUtils() {
    }

    public static String cleanComment(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String text = raw.replace("\r", " ").replace("\n", " ").replace("\t", " ");
        text = text.replace("*/", "* /");
        text = text.replaceAll("\\s{2,}", " ").trim();
        if (text.isEmpty()) {
            return fallback;
        }
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    /**
     * 拼进 JS 单引号字符串时使用。
     *
     * <p>引号和反斜杠会截断字符串；尖括号虽然不会打断 JS，但生成物会进入
     * Vue 单文件组件，注释里若出现 {@code </script>} 会提前结束脚本块，
     * 因此统一替换成视觉相近的安全字符。</p>
     */
    public static String jsLabel(String raw, String fallback) {
        String text = cleanComment(raw, fallback);
        return text.replace("\\", "/")
                .replace("'", "’")
                .replace("<", "‹")
                .replace(">", "›");
    }

    /** 放进 HTML 文本 / 双引号属性时使用。 */
    public static String html(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** 放进 Java 双引号字符串字面量时使用。 */
    public static String javaString(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 放进 SQL 单引号字符串时使用。 */
    public static String sqlString(String raw) {
        return raw == null ? "" : raw.replace("'", "''");
    }
}
