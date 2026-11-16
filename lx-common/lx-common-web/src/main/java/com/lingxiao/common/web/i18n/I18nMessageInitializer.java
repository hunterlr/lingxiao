package com.lingxiao.common.web.i18n;

import com.lingxiao.common.core.i18n.MessageUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 把 Spring 的 {@link MessageSource} 接到 {@link MessageUtils} 上。
 *
 * <p>为什么单独一个 Bean：{@code lx-common-core} 不能依赖 Spring，
 * 只能暴露一个静态入口；静态入口需要有人“开机时插上电”。
 * 如果把 MessageSource 注入 {@code I18nConfig} 的构造器，会形成循环依赖
 * （配置类依赖 MessageSource Bean，而该 Bean 又由配置类创建），
 * 所以拆成独立组件，让 Spring 自己排顺序。</p>
 *
 * <p>语言取 {@code LocaleContextHolder}：由 {@code DispatcherServlet}
 * 在处理请求前按 {@link LingxiaoLocaleResolver} 的解析结果写入。</p>
 */
@Component
public class I18nMessageInitializer {

    private final MessageSource messageSource;

    public I18nMessageInitializer(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @PostConstruct
    public void init() {
        MessageUtils.setResolver((key, args) ->
                messageSource.getMessage(key, args, null, LocaleContextHolder.getLocale()));
    }
}
