package com.lingxiao.common.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 参数读取 SPI 的默认实现测试。
 *
 * <p>{@link ConfigProvider#getIntValue} / {@link ConfigProvider#getBooleanValue}
 * 是所有业务模块读参数时的兜底逻辑：数据库里的脏值（空串、非数字、大小写混写）
 * 不能把请求打成 500，必须回退默认值。</p>
 */
@DisplayName("ConfigProvider 默认解析")
class ConfigProviderTest {

    private static ConfigProvider providerOf(String value) {
        return key -> value;
    }

    @Test
    @DisplayName("getIntValue：正常数字、空值、非数字分别处理")
    void intValue() {
        assertThat(providerOf(" 30 ").getIntValue("k", 10)).isEqualTo(30);
        assertThat(providerOf(null).getIntValue("k", 10)).isEqualTo(10);
        assertThat(providerOf("  ").getIntValue("k", 10)).isEqualTo(10);
        assertThat(providerOf("abc").getIntValue("k", 10)).isEqualTo(10);
    }

    @Test
    @DisplayName("getBooleanValue：兼容 true/1/Y 与 false/0/N")
    void booleanValue() {
        for (String value : new String[]{"true", "TRUE", "1", "y", "Y", " true "}) {
            assertThat(providerOf(value).getBooleanValue("k", false))
                    .as("值 %s 应解析为 true", value)
                    .isTrue();
        }
        for (String value : new String[]{"false", "FALSE", "0", "n", "N", " false "}) {
            assertThat(providerOf(value).getBooleanValue("k", true))
                    .as("值 %s 应解析为 false", value)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("getBooleanValue：null 与无法识别的值回退默认值")
    void booleanFallback() {
        assertThat(providerOf(null).getBooleanValue("k", true)).isTrue();
        assertThat(providerOf("maybe").getBooleanValue("k", false)).isFalse();
        assertThat(providerOf("maybe").getBooleanValue("k", true)).isTrue();
    }
}
