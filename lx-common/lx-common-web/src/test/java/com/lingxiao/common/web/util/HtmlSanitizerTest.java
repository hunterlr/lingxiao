package com.lingxiao.common.web.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 富文本白名单清洗测试。
 *
 * <p>这是安全回归，不是“格式化”测试：公告正文由富文本编辑器提交，
 * 只要服务端漏掉一次清洗，任何有编辑权限的账号都能在其他管理员浏览器里执行脚本。
 * 用例覆盖 {@code <script>}、事件属性、伪协议、iframe、内联样式五类载荷。</p>
 */
@DisplayName("HtmlSanitizer 富文本清洗")
class HtmlSanitizerTest {

    @Test
    @DisplayName("script 标签与内容被移除")
    void removesScript() {
        String cleaned = HtmlSanitizer.clean("<p>hello</p><script>alert(1)</script>");

        assertThat(cleaned).doesNotContain("<script");
        assertThat(cleaned).doesNotContain("alert(1)");
        assertThat(cleaned).contains("<p>hello</p>");
    }

    @Test
    @DisplayName("事件属性 onerror / onclick 被移除")
    void removesEventHandlers() {
        String cleaned = HtmlSanitizer.clean(
                "<img src=\"/api/file/1\" onerror=\"alert(1)\"><p onclick=\"x()\">t</p>");

        assertThat(cleaned).doesNotContain("onerror");
        assertThat(cleaned).doesNotContain("onclick");
        assertThat(cleaned).contains("src=\"/api/file/1\"");
    }

    @Test
    @DisplayName("javascript: 伪协议被移除，正常 https 链接保留")
    void removesJavascriptProtocol() {
        String cleaned = HtmlSanitizer.clean(
                "<a href=\"javascript:alert(1)\">bad</a><a href=\"https://example.com\" target=\"_blank\">ok</a>");

        assertThat(cleaned).doesNotContain("javascript:");
        assertThat(cleaned).contains("href=\"https://example.com\"");
        assertThat(cleaned).contains("target=\"_blank\"");
    }

    @Test
    @DisplayName("所有链接强制 rel=nofollow noopener noreferrer")
    void enforcesRel() {
        String cleaned = HtmlSanitizer.clean("<a href=\"https://example.com\">x</a>");

        assertThat(cleaned).contains("rel=\"nofollow noopener noreferrer\"");
    }

    @Test
    @DisplayName("iframe / object / embed 整段移除")
    void removesEmbeds() {
        String cleaned = HtmlSanitizer.clean(
                "<iframe src=\"https://evil\"></iframe><object data=\"x\"></object><embed src=\"y\">");

        assertThat(cleaned).doesNotContain("<iframe");
        assertThat(cleaned).doesNotContain("<object");
        assertThat(cleaned).doesNotContain("<embed");
    }

    @Test
    @DisplayName("style / class 不在白名单，内联样式被剥离")
    void removesInlineStyle() {
        String cleaned = HtmlSanitizer.clean(
                "<p style=\"color:red;background:url(javascript:alert(1))\" class=\"x\">t</p>");

        assertThat(cleaned).doesNotContain("style=");
        assertThat(cleaned).doesNotContain("class=");
        assertThat(cleaned).contains("<p>t</p>");
    }

    @Test
    @DisplayName("安全标签与相对图片地址保留（编辑器插图依赖 /api/file）")
    void keepsSafeContent() {
        String cleaned = HtmlSanitizer.clean(
                "<h2>标题</h2><p><strong>粗体</strong><em>斜体</em></p>"
                        + "<ul><li>一</li></ul><table><tr><td colspan=\"2\">格</td></tr></table>");

        assertThat(cleaned).contains("<h2>标题</h2>");
        assertThat(cleaned).contains("<strong>粗体</strong>");
        assertThat(cleaned).contains("<em>斜体</em>");
        assertThat(cleaned).contains("<li>一</li>");
        assertThat(cleaned).contains("colspan=\"2\"");
    }

    @Test
    @DisplayName("已转义的文本不会被当成标签执行")
    void keepsEscapedText() {
        String cleaned = HtmlSanitizer.clean("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>");

        assertThat(cleaned).doesNotContain("<script");
        assertThat(cleaned).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("null / 空串原样返回：可空字段不要被改成空串")
    void nullAndEmpty() {
        assertThat(HtmlSanitizer.clean(null)).isNull();
        assertThat(HtmlSanitizer.clean("")).isEmpty();
    }
}
