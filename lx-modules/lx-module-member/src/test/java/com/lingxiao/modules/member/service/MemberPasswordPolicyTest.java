package com.lingxiao.modules.member.service;

import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会员密码策略测试。
 *
 * <p>三条红线：默认 basic（8 位 + 字母 + 数字）；策略值非法回退 basic；
 * 登录不校验，只有“设置新密码”才校验（由 Service 调用点保证）。</p>
 */
@DisplayName("MemberPasswordPolicy 会员密码策略")
class MemberPasswordPolicyTest {

    private static MemberPasswordPolicy policyOf(String value) {
        ConfigProvider provider = mock(ConfigProvider.class);
        when(provider.getValue(MemberPasswordPolicy.CONFIG_KEY)).thenReturn(value);
        return new MemberPasswordPolicy(provider);
    }

    @Test
    @DisplayName("缺失配置按 basic：8 位且含字母和数字")
    void defaultsToBasic() {
        MemberPasswordPolicy policy = policyOf(null);
        assertThat(policy.resolvePolicy()).isEqualTo("basic");
        assertThatCode(() -> policy.validate("abc12345")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("abcdefgh"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("valid.password.policy.basic"));
    }

    @Test
    @DisplayName("basic：少于 8 位或纯数字都拒绝")
    void basicRejectsWeakPasswords() {
        MemberPasswordPolicy policy = policyOf("basic");
        assertThatThrownBy(() -> policy.validate("abc123"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> policy.validate("12345678"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("strong：必须同时含大小写、数字和符号")
    void strongRequiresAllCharacterClasses() {
        MemberPasswordPolicy policy = policyOf("strong");
        assertThatThrownBy(() -> policy.validate("Abcdef1234"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("valid.password.policy.strong"));
        assertThatCode(() -> policy.validate("Abcdef123!")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("off：不校验任何密码")
    void offSkipsValidation() {
        assertThatCode(() -> policyOf("off").validate("1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非法配置回退 basic，而不是放行")
    void invalidValueFallsBackToBasic() {
        MemberPasswordPolicy policy = policyOf("whatever");
        assertThat(policy.resolvePolicy()).isEqualTo("basic");
        assertThatThrownBy(() -> policy.validate("short"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }
}
