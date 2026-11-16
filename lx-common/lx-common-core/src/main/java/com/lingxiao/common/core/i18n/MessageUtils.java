package com.lingxiao.common.core.i18n;

/**
 * 文案工具：按 key 取当前请求语言的文案。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>永远不抛异常</b>。缺 key、语言包没加载、单元测试里没初始化，
 *       都退回到调用方给的默认文案（通常是 {@code ErrorCode} 里的中文），
 *       保证“翻译挂了不影响主流程”；</li>
 *   <li>语言取自 {@code LocaleContextHolder}，由 {@code LingxiaoLocaleResolver} 按
 *       {@code X-Lang > ?lang= > Accept-Language > zh-CN} 解析后写入；</li>
 *   <li>定时任务、异步线程没有请求上下文，会拿到系统默认语言，这是预期行为。</li>
 * </ul>
 */
public final class MessageUtils {

    private static volatile MessageResolver resolver;

    private MessageUtils() {
    }

    /** 由 {@code lx-common-web} 在启动时注入；测试里不注入也能跑。 */
    public static void setResolver(MessageResolver messageResolver) {
        resolver = messageResolver;
    }

    /** 是否已接入语言包（排查“文案没翻译”时先看这个）。 */
    public static boolean isInitialized() {
        return resolver != null;
    }

    /**
     * 取文案。
     *
     * @param key            语言包键
     * @param defaultMessage 兜底文案，key 为空或取不到时返回它
     */
    public static String message(String key, String defaultMessage) {
        return message(key, null, defaultMessage);
    }

    /**
     * 取带占位参数的文案。
     *
     * @param key            语言包键
     * @param args           占位参数，例如 {@code {0}} 会被 args[0] 替换
     * @param defaultMessage 兜底文案
     */
    public static String message(String key, Object[] args, String defaultMessage) {
        if (key == null || key.isEmpty()) {
            return defaultMessage;
        }
        MessageResolver current = resolver;
        if (current == null) {
            return defaultMessage;
        }
        try {
            String text = current.resolve(key, args);
            return text == null || text.isEmpty() ? defaultMessage : text;
        } catch (RuntimeException e) {
            return defaultMessage;
        }
    }

    /** 错误码对应的语言包键，例如 {@code error.2001}。 */
    public static String errorKey(int code) {
        return "error." + code;
    }
}
