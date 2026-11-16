package com.lingxiao.common.web.i18n;

import com.lingxiao.common.core.enums.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 语言包完整性测试。
 *
 * <p>三个约束：</p>
 * <ol>
 *   <li>中英文键集合必须完全一致——否则英文环境会随机冒中文；</li>
 *   <li>每个 {@link ErrorCode} 都必须有 {@code error.<code>}——否则前端拿到的是枚举兜底中文；</li>
 *   <li>带占位符的文案必须真的把参数填进去。</li>
 * </ol>
 */
@DisplayName("语言包 messages*.properties")
class I18nMessageSourceTest {

    private static final Locale ZH = Locale.SIMPLIFIED_CHINESE;
    private static final Locale EN = Locale.US;

    private static ResourceBundleMessageSource messageSource;

    @BeforeAll
    static void setUp() {
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(I18nConfig.MESSAGE_BASENAME);
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);
    }

    private static Set<String> keysOf(String file) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource(file).getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new TreeSet<>(properties.stringPropertyNames());
    }

    @Test
    @DisplayName("中英文键集合一致（防止只加了一边）")
    void keysAreInSync() throws Exception {
        Set<String> zh = keysOf("i18n/messages.properties");
        Set<String> en = keysOf("i18n/messages_en_US.properties");

        assertThat(zh).isNotEmpty();
        assertThat(en).containsExactlyInAnyOrderElementsOf(zh);
    }

    @Test
    @DisplayName("每个 ErrorCode 都有对应的 error.<code> 文案")
    void everyErrorCodeHasMessage() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            String key = "error." + errorCode.getCode();
            assertThat(messageSource.getMessage(key, null, null, ZH))
                    .as("中文缺失: " + key)
                    .isNotBlank();
            assertThat(messageSource.getMessage(key, null, null, EN))
                    .as("英文缺失: " + key)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("同一 key 在中英文下返回各自语言")
    void resolvesByLocale() {
        assertThat(messageSource.getMessage("error.2001", null, ZH)).isEqualTo("用户名或密码错误");
        assertThat(messageSource.getMessage("error.2001", null, EN)).isEqualTo("Incorrect username or password");

        assertThat(messageSource.getMessage("valid.username.required", null, ZH)).isEqualTo("用户名不能为空");
        assertThat(messageSource.getMessage("valid.username.required", null, EN))
                .isEqualTo("Username must not be empty");
    }

    @Test
    @DisplayName("带占位符的文案会替换参数")
    void formatsArguments() {
        assertThat(messageSource.getMessage("error.2004.locked", new Object[]{10}, ZH))
                .isEqualTo("账号已锁定，请 10 分钟后再试");
        assertThat(messageSource.getMessage("error.2004.locked", new Object[]{10}, EN))
                .isEqualTo("Account is locked. Please try again in 10 minute(s)");
    }

    @Test
    @DisplayName("缺 key 时返回 null，而不是把 key 当文案弹给用户")
    void missingKeyReturnsNull() {
        assertThat(messageSource.getMessage("error.not.exists", null, null, EN)).isNull();
    }
}
