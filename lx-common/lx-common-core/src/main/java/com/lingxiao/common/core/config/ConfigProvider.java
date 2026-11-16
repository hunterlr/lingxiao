package com.lingxiao.common.core.config;

import java.util.Locale;

/**
 * 系统参数读取 SPI。
 *
 * <p><b>为什么放在 common-core：</b>文件、CMS 等业务模块需要读“参数设置”里的值，
 * 但它们不应该依赖 {@code lx-module-system}（否则模块之间会形成环）。
 * 这里只定义最小读取能力，由系统模块提供实现；没有实现时调用方回退自己的
 * YAML / 默认值，启动不会因为缺 Bean 失败。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>只读。写入必须走参数管理接口，保证控件类型 / 选项 / 值校验不被绕过；</li>
 *   <li>实现方不应缓存，或在参数更新时主动失效，否则改了参数要重启才生效；</li>
 *   <li>返回值可能为 {@code null}，调用方必须给默认值。</li>
 * </ul>
 */
public interface ConfigProvider {

    /** 按 key 取原始字符串；不存在返回 {@code null}。 */
    String getValue(String key);

    /** 按 key 取整数；缺失或不是合法整数时返回默认值。 */
    default int getIntValue(String key, int defaultValue) {
        String value = getValue(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 按 key 取布尔值。
     *
     * <p>同时接受 {@code true/false}、{@code 1/0}、{@code Y/N}，
     * 因为历史参数和别的系统导入的数据可能用不同写法；无法识别时回退默认值。</p>
     */
    default boolean getBooleanValue(String key, boolean defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "y".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "n".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }
}
