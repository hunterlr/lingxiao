package com.lingxiao.modules.i18n.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 数据库优先 MessageSource 测试。
 *
 * <p>锁定三条回退顺序：DB > 资源包 > 默认文案；并确认缺失时才记日志。</p>
 */
@DisplayName("I18nMessageSource")
class I18nMessageSourceTest {

    private Map<String, String> database;

    private String lastLanguage;

    private I18nMissingTracker tracker;

    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        database = new HashMap<>();
        database.put("error.1001", "DB: 参数不正确");
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String find(String languageCode, String keyCode) {
                lastLanguage = languageCode;
                return "en-US".equals(languageCode) ? database.get(keyCode) : null;
            }

            @Override
            public Map<String, String> published(String languageCode) {
                return "en-US".equals(languageCode) ? database : java.util.Collections.emptyMap();
            }
        };
        tracker = mock(I18nMissingTracker.class);
        ResourceBundleMessageSource fallback = new ResourceBundleMessageSource();
        fallback.setBasenames("i18n/messages");
        fallback.setDefaultEncoding("UTF-8");
        fallback.setFallbackToSystemLocale(false);
        messageSource = new I18nMessageSource(fallback, provider, tracker);
    }

    @Test
    @DisplayName("数据库命中时优先于资源包")
    void databaseWins() {
        assertThat(messageSource.getMessage("error.1001", null, Locale.US))
                .isEqualTo("DB: 参数不正确");
    }

    @Test
    @DisplayName("数据库缺失时回退资源包")
    void fallbackToBundle() {
        String message = messageSource.getMessage("error.1002", null, "fallback", Locale.US);
        assertThat(message).isNotEqualTo("fallback").isNotEqualTo("error.1002");
    }

    @Test
    @DisplayName("数据库与资源包都缺失时用默认文案并记录")
    void missingUsesDefaultAndRecords() {
        String message = messageSource.getMessage("nope.missing.key", null, "兜底", Locale.US);
        assertThat(message).isEqualTo("兜底");
        verify(tracker).record(eq("en-US"), eq("nope.missing.key"), anyString());
    }

    @Test
    @DisplayName("中文 Locale 归一到 zh-CN")
    void localeNormalized() {
        String message = messageSource.getMessage("error.1001", null, "fallback", Locale.SIMPLIFIED_CHINESE);
        assertThat(message).isNotEqualTo("fallback").isNotEqualTo("error.1001");
        assertThat(lastLanguage).isEqualTo("zh-CN");
    }
}
