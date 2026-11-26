package com.lingxiao.common.web.i18n;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 多语言基础装配。
 *
 * <p>只做两件事：把 {@code i18n/messages*.properties} 装成 {@link MessageSource}，
 * 再注册自己的 {@link LocaleResolver}。参数校验、{@code R}、全局异常处理器
 * 都复用这一个 MessageSource，避免“接口是英文、校验还是中文”。</p>
 *
 * <p>注意：不要额外定义 {@code Validator} Bean。Spring Boot 的
 * {@code ValidationAutoConfiguration} 会自动把容器里的 MessageSource 接到
 * Hibernate Validator 上，并用 {@code LocaleContextHolder} 的当前语言解析
 * {@code {key}}；自己 new 一个 LocalValidatorFactoryBean 反而容易漏配。</p>
 */
@Configuration
public class I18nConfig {

    /** 语言包路径（不带 {@code .properties}）；默认文件即中文。 */
    public static final String MESSAGE_BASENAME = "i18n/messages";

    /**
     * 语言包。
     *
     * <ul>
     *   <li>{@code defaultEncoding=UTF-8}：properties 里有中文，必须显式声明；</li>
     *   <li>{@code fallbackToSystemLocale=false}：否则服务器是英文环境时，
     *       中文请求会莫名其妙变成英文；</li>
     *   <li>{@code useCodeAsDefaultMessage=false}：缺 key 时返回 null 让调用方兜底，
     *       而不是把 {@code error.2001} 直接弹给用户。</li>
     * </ul>
     */
    @Bean
    @ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "false", matchIfMissing = true)
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(MESSAGE_BASENAME);
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    /** 按请求头 / 参数 / Accept-Language 解析语言，见 {@link LingxiaoLocaleResolver}。 */
    @Bean
    public LocaleResolver localeResolver() {
        return new LingxiaoLocaleResolver();
    }
}
