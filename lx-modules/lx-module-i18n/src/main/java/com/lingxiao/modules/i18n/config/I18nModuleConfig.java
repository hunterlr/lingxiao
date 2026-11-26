package com.lingxiao.modules.i18n.config;

import com.lingxiao.common.web.i18n.I18nConfig;
import com.lingxiao.modules.i18n.message.I18nMessageSource;
import com.lingxiao.modules.i18n.message.I18nMissingTracker;
import com.lingxiao.modules.i18n.message.TranslationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多语言模块装配。
 *
 * <p>开启 {@code lx.module.i18n.enabled=true} 时，用 {@link I18nMessageSource}
 * 顶替 common-web 的资源包 MessageSource；关闭时该 Bean 不创建，
 * 由 {@code I18nConfig} 继续提供纯资源包实现。</p>
 */
@Configuration
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nModuleConfig {

    /**
     * Bean 名必须是 {@code messageSource}。
     *
     * <p>Spring Boot 的 {@code ValidationAutoConfiguration} 把
     * {@code ApplicationContext} 当成 {@link MessageSource} 传给 Hibernate Validator；
     * 而 {@code ApplicationContext#getMessage} 只委托给名为 {@code messageSource}
     * 的 Bean。改名成 {@code i18nMessageSource} 会让参数校验退回空文案，
     * 出现 {@code {valid.username.required}} 这种原样占位符。</p>
     */
    @Bean(name = "messageSource")
    @Primary
    public MessageSource i18nMessageSource(TranslationProvider translationProvider,
                                           I18nMissingTracker missingTracker) {
        MessageSource fallback = I18nMessageSource.resourceBundleFallback(I18nConfig.MESSAGE_BASENAME);
        return new I18nMessageSource(fallback, translationProvider, missingTracker);
    }
}
