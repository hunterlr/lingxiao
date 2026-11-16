package com.lingxiao.common.core.i18n;

/**
 * 文案解析器。
 *
 * <p>{@code lx-common-core} 不依赖 Spring，所以这里只定义最小接口；
 * 真正的实现由 {@code lx-common-web} 的 {@code I18nConfig} 提供（内部走 MessageSource）。</p>
 *
 * <p>这样 Service 层、异常、{@code R} 都能用同一套 key 取文案，
 * 又不会把 Spring 拖进 core 模块。</p>
 */
@FunctionalInterface
public interface MessageResolver {

    /**
     * 按 key 取文案。
     *
     * @param key  语言包键，例如 {@code error.2001}
     * @param args 占位参数，可为 null
     * @return 翻译后的文案；找不到时返回 null 或抛异常，由调用方兜底
     */
    String resolve(String key, Object[] args);
}
