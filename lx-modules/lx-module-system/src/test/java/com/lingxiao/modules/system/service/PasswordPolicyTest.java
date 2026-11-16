package com.lingxiao.modules.system.service;

import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 密码复杂度策略测试。
 *
 * <p>三条产品红线：默认 basic（8 位 + 字母 + 数字）；策略值非法回退 basic；
 * 登录不校验、只有“设置新密码”才校验（后者由 Service 调用点保证，本测试锁定解析逻辑）。</p>
 */
@DisplayName("PasswordPolicy 密码策略")
class PasswordPolicyTest {

    private static PasswordPolicy policyOf(String value) {
        ConfigProvider provider = mock(ConfigProvider.class);
        when(provider.getValue(PasswordPolicy.CONFIG_KEY)).thenReturn(value);
        return new PasswordPolicy(provider);
    }

    @Test
    @DisplayName("默认 basic：至少 8 位且同时含字母和数字")
    void basicPolicy() {
        PasswordPolicy policy = policyOf(null);

        assertThat(policy.resolvePolicy()).isEqualTo("basic");
        assertThatCode(() -> policy.validate("abc12345")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("A1b2C3d4")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("basic：长度不足 / 纯字母 / 纯数字都被拒绝，错误码 1001")
    void basicRejectsWeakPasswords() {
        PasswordPolicy policy = policyOf("basic");

        ServiceException tooShort = catchThrowableOfType(
                () -> policy.validate("abc1234"), ServiceException.class);
        assertThat(tooShort.getCode()).isEqualTo(1001);
        // 文案在多语言改造后由语言包提供，这里只锁“命中了哪个键”
        assertThat(tooShort.getMessageKey()).isEqualTo("valid.password.policy.basic");

        assertThatThrownBy(() -> policy.validate("abcdefgh"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("messageKey", "valid.password.policy.basic");
        assertThatThrownBy(() -> policy.validate("12345678"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("messageKey", "valid.password.policy.basic");
        assertThatThrownBy(() -> policy.validate(null))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("off：不校验复杂度（长度由 DTO 的 6~64 负责）")
    void offPolicy() {
        PasswordPolicy policy = policyOf("OFF");

        assertThat(policy.resolvePolicy()).isEqualTo("off");
        assertThatCode(() -> policy.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("a")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("strong：至少 10 位且含大写/小写/数字/符号")
    void strongPolicy() {
        PasswordPolicy policy = policyOf("strong");

        assertThat(policy.resolvePolicy()).isEqualTo("strong");
        assertThatCode(() -> policy.validate("Abcdef123!")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("abcdef123!"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("messageKey", "valid.password.policy.strong");
        assertThatThrownBy(() -> policy.validate("ABCDEF123!"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("messageKey", "valid.password.policy.strong");
        assertThatThrownBy(() -> policy.validate("Abcdefghij"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("messageKey", "valid.password.policy.strong");
        assertThatThrownBy(() -> policy.validate("Abcdef1234"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("messageKey", "valid.password.policy.strong");
    }

    @Test
    @DisplayName("参数值非法 / 大小写混写 / 前后空格：按 basic 处理")
    void invalidValueFallsBackToBasic() {
        assertThat(policyOf("weird").resolvePolicy()).isEqualTo("basic");
        assertThat(policyOf("  STRONG  ").resolvePolicy()).isEqualTo("strong");
        assertThat(policyOf("   ").resolvePolicy()).isEqualTo("basic");
    }
}
