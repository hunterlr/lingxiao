package com.lingxiao.modules.generator.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注释 / 文本转义测试。
 *
 * <p>数据库注释会同时进入 Java 注释、Vue 模板和 JS 字符串。
 * 这里任何一个转义漏掉，生成物都会“看起来成功、编译或渲染失败”。</p>
 */
@DisplayName("TextUtils 文本清理与转义")
class TextUtilsTest {

    @Test
    @DisplayName("cleanComment：换行 / 制表符折叠成空格")
    void cleanCommentWhitespace() {
        assertThat(TextUtils.cleanComment("用户\r\n名称\t说明", "fallback"))
                .isEqualTo("用户 名称 说明");
        assertThat(TextUtils.cleanComment("  a   b  ", "fallback")).isEqualTo("a b");
    }

    @Test
    @DisplayName("cleanComment：*/ 必须拆开，否则 Javadoc 提前结束")
    void cleanCommentJavadoc() {
        assertThat(TextUtils.cleanComment("a */ b", "fallback")).isEqualTo("a * / b");
    }

    @Test
    @DisplayName("cleanComment：空串 / null / 全空白回退默认值")
    void cleanCommentFallback() {
        assertThat(TextUtils.cleanComment(null, "fallback")).isEqualTo("fallback");
        assertThat(TextUtils.cleanComment("", "fallback")).isEqualTo("fallback");
        assertThat(TextUtils.cleanComment("   ", "fallback")).isEqualTo("fallback");
    }

    @Test
    @DisplayName("cleanComment：超长注释截断到 500 字符")
    void cleanCommentTruncate() {
        String raw = repeat('a', 600);

        assertThat(TextUtils.cleanComment(raw, "fallback")).hasSize(500);
    }

    @Test
    @DisplayName("jsLabel：引号、反斜杠、尖括号都替换，避免截断字符串/脚本块")
    void jsLabel() {
        assertThat(TextUtils.jsLabel("a'b\\c</script>", "fallback"))
                .isEqualTo("a’b/c‹/script›");
        assertThat(TextUtils.jsLabel(null, "用户")).isEqualTo("用户");
    }

    @Test
    @DisplayName("html：五个字符全部转义，防止标签注入")
    void html() {
        assertThat(TextUtils.html("<a href=\"x\">'&'</a>"))
                .isEqualTo("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;");
        assertThat(TextUtils.html(null)).isEmpty();
        assertThat(TextUtils.html("纯文本")).isEqualTo("纯文本");
    }

    @Test
    @DisplayName("javaString：反斜杠与双引号转义")
    void javaString() {
        assertThat(TextUtils.javaString("say \"hi\"")).isEqualTo("say \\\"hi\\\"");
        assertThat(TextUtils.javaString("a\\b")).isEqualTo("a\\\\b");
        assertThat(TextUtils.javaString(null)).isEmpty();
    }

    @Test
    @DisplayName("sqlString：单引号翻倍，防止菜单 SQL 被截断")
    void sqlString() {
        assertThat(TextUtils.sqlString("O'Reilly")).isEqualTo("O''Reilly");
        assertThat(TextUtils.sqlString(null)).isEmpty();
        assertThat(TextUtils.sqlString("普通")).isEqualTo("普通");
    }

    private static String repeat(char c, int times) {
        char[] chars = new char[times];
        for (int i = 0; i < times; i++) {
            chars[i] = c;
        }
        return new String(chars);
    }
}
