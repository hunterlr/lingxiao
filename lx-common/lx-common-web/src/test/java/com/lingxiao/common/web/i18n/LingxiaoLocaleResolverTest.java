package com.lingxiao.common.web.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求语言解析测试。
 *
 * <p>优先级是产品契约：{@code X-Lang > ?lang= > Accept-Language > zh-CN}。
 * 这里逐条锁住，避免以后有人“顺手”调换顺序导致前端切了语言、后端还返回中文。</p>
 */
@DisplayName("LingxiaoLocaleResolver 语言解析")
class LingxiaoLocaleResolverTest {

    private final LingxiaoLocaleResolver resolver = new LingxiaoLocaleResolver();

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    @Test
    @DisplayName("X-Lang 优先级最高：en-US 压过 Accept-Language 的 zh")
    void headerWins() {
        MockHttpServletRequest request = request();
        request.addHeader(LingxiaoLocaleResolver.LANG_HEADER, "en-US");
        request.addHeader("Accept-Language", "zh-CN,zh;q=0.9");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
    }

    @Test
    @DisplayName("没有 X-Lang 时用 ?lang=")
    void paramUsedWhenHeaderMissing() {
        MockHttpServletRequest request = request();
        request.setParameter("lang", "en-US");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
    }

    @Test
    @DisplayName("X-Lang 优先于 ?lang=")
    void headerBeatsParam() {
        MockHttpServletRequest request = request();
        request.addHeader(LingxiaoLocaleResolver.LANG_HEADER, "zh-CN");
        request.setParameter("lang", "en-US");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    @DisplayName("都没带：用 Accept-Language")
    void acceptLanguageFallback() {
        MockHttpServletRequest request = request();
        request.addHeader("Accept-Language", "en-GB,en;q=0.9");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
    }

    @Test
    @DisplayName("完全不认识：兜底 zh-CN")
    void defaultIsChinese() {
        MockHttpServletRequest request = request();
        request.addHeader("Accept-Language", "fr-FR,fr;q=0.9");
        request.addHeader(LingxiaoLocaleResolver.LANG_HEADER, "fr");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    @DisplayName("语言标签归一化：zh / zh_CN / en / EN-us 都能认")
    void normalizeTags() {
        assertThat(LingxiaoLocaleResolver.parse("zh")).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(LingxiaoLocaleResolver.parse("zh_CN")).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(LingxiaoLocaleResolver.parse("en")).isEqualTo(Locale.US);
        assertThat(LingxiaoLocaleResolver.parse("EN-us")).isEqualTo(Locale.US);
        assertThat(LingxiaoLocaleResolver.parse("  en-US  ")).isEqualTo(Locale.US);
        assertThat(LingxiaoLocaleResolver.parse("")).isNull();
        assertThat(LingxiaoLocaleResolver.parse(null)).isNull();
        assertThat(LingxiaoLocaleResolver.parse("ja-JP")).isNull();
    }

    @Test
    @DisplayName("setLocale 是无操作：语言不落 Cookie / Session")
    void setLocaleIsNoop() {
        MockHttpServletRequest request = request();
        // MockHttpServletRequest 默认 Accept-Language 是 en；这里给一个不支持的语言，验证回退中文
        request.addHeader("Accept-Language", "fr-FR");

        resolver.setLocale(request, null, Locale.US);

        assertThat(request.getCookies()).isNullOrEmpty();
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }
}
