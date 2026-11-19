package com.lingxiao.modules.system.service;

import com.lingxiao.common.core.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会话策略测试。
 *
 * <p>核心约定：参数缺失 / 空白 / 非法值都按单会话处理——
 * 多端是显式开启的能力，不能因为一个参数写错就悄悄放开。</p>
 */
@DisplayName("SessionPolicy 会话策略")
class SessionPolicyTest {

    private static SessionPolicy policyOf(String value) {
        ConfigProvider provider = mock(ConfigProvider.class);
        when(provider.getValue(SessionPolicy.CONFIG_KEY)).thenReturn(value);
        return new SessionPolicy(provider);
    }

    @Test
    @DisplayName("参数缺失：默认单会话（新登录踢旧会话）")
    void defaultsToSingleSession() {
        assertThat(policyOf(null).isMultiDeviceLoginEnabled()).isFalse();
    }

    @Test
    @DisplayName("false / 空白 / 非法值：仍是单会话")
    void invalidValuesFallBackToSingleSession() {
        assertThat(policyOf("false").isMultiDeviceLoginEnabled()).isFalse();
        assertThat(policyOf("   ").isMultiDeviceLoginEnabled()).isFalse();
        assertThat(policyOf("yes").isMultiDeviceLoginEnabled()).isFalse();
    }

    @Test
    @DisplayName("true：显式开启多端，大小写与前后空格不敏感")
    void trueEnablesMultiDevice() {
        assertThat(policyOf("true").isMultiDeviceLoginEnabled()).isTrue();
        assertThat(policyOf(" TRUE ").isMultiDeviceLoginEnabled()).isTrue();
    }
}
