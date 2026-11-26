package com.lingxiao.modules.i18n.support;

import com.lingxiao.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CSV 编解码测试。
 *
 * <p>Excel / WPS 导出的 CSV 会带 BOM、引号、字段内逗号与换行，
 * 这些必须在导入前就处理对，否则错位会写坏译文。</p>
 */
@DisplayName("I18nCsvCodec")
class I18nCsvCodecTest {

    @Test
    @DisplayName("解析 BOM、引号、逗号与换行")
    void parseComplexCsv() {
        String content = "\uFEFFnamespace,key,zh-CN,en-US,remark\r\n"
                + "error,error.1001,\"参数不正确, 请检查\",\"Bad request\",\"备注\"\n"
                + "cms,cms.article.notfound,\"文章不存在\",\"Article \"\"not\"\" found\",\n";
        List<List<String>> rows = I18nCsvCodec.parse(content);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsExactly("namespace", "key", "zh-CN", "en-US", "remark");
        assertThat(rows.get(1)).containsExactly("error", "error.1001", "参数不正确, 请检查", "Bad request", "备注");
        assertThat(rows.get(2).get(3)).isEqualTo("Article \"not\" found");
    }

    @Test
    @DisplayName("引号内换行不会被拆行")
    void parseQuotedNewline() {
        String content = "a,b\n1,\"line1\nline2\"\n";
        List<List<String>> rows = I18nCsvCodec.parse(content);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get(1)).isEqualTo("line1\nline2");
    }

    @Test
    @DisplayName("导出时按需加引号并转义")
    void encodeCell() {
        assertThat(I18nCsvCodec.cell("plain")).isEqualTo("plain");
        assertThat(I18nCsvCodec.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(I18nCsvCodec.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(I18nCsvCodec.line(java.util.Arrays.asList("a", "b,c", null)))
                .isEqualTo("a,\"b,c\",");
    }

    @Test
    @DisplayName("未闭合引号按格式错误处理")
    void unclosedQuote() {
        assertThatThrownBy(() -> I18nCsvCodec.parse("a,b\n1,\"oops"))
                .isInstanceOf(ServiceException.class);
    }
}
