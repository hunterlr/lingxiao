package com.lingxiao.modules.i18n.message;

import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * 数据库优先的 MessageSource。
 *
 * <p>查找顺序（docs/15）：</p>
 * <ol>
 *   <li>数据库已发布译文；</li>
 *   <li>父 MessageSource（资源包 {@code i18n/messages*.properties}）；</li>
 *   <li>都没命中时返回 null，由调用方用 {@code defaultMessage} 或 key 兜底。</li>
 * </ol>
 *
 * <p>不把 {@code useCodeAsDefaultMessage} 打开：缺 key 时直接把
 * {@code error.2001} 显示给用户，比“未知错误”更难排查。</p>
 */
public class I18nMessageSource extends AbstractMessageSource {

    private final TranslationProvider translationProvider;

    private final I18nMissingTracker missingTracker;

    public I18nMessageSource(MessageSource fallback, TranslationProvider translationProvider,
                             I18nMissingTracker missingTracker) {
        this.translationProvider = translationProvider;
        this.missingTracker = missingTracker;
        setParentMessageSource(fallback);
        setUseCodeAsDefaultMessage(false);
    }

    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        String value = translationProvider.find(languageCode(locale), code);
        return value == null ? null : createMessageFormat(value, locale);
    }

    /**
     * 最终兜底为空时记录缺失；此时数据库和资源包都没有该 key。
     */
    @Override
    protected String getMessageInternal(String code, Object[] args, Locale locale) {
        String message = super.getMessageInternal(code, args, locale);
        if (message == null && code != null && missingTracker != null) {
            Locale effective = locale == null ? LocaleContextHolder.getLocale() : locale;
            missingTracker.record(languageCode(effective), code, effective.toLanguageTag());
        }
        return message;
    }

    /** DB 的语言标识是 BCP 47，而 Locale 可能是 zh_CN / en。 */
    private String languageCode(Locale locale) {
        return com.lingxiao.modules.i18n.support.I18nLanguageCodes.fromLocale(locale);
    }

    /**
     * 资源包兜底：与 I18nConfig 的配置保持一致。
     *
     * <p>单独 new 一份，是因为模块开启时 common-web 的 messageSource Bean 不装配，
     * 不能直接注入。</p>
     *
     * @param basename 资源包基名，通常为 {@code i18n/messages}
     */
    public static MessageSource resourceBundleFallback(String basename) {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(basename);
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }
}
