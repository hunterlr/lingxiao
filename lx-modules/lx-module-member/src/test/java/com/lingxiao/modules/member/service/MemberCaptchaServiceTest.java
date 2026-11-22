package com.lingxiao.modules.member.service;

import cn.dev33.satoken.SaManager;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberCaptchaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会员验证码测试。
 *
 * <p>验证码存 Sa-Token DAO（单机内存 / Redis 自动切换），本测试直接用默认 DAO。
 * 重点锁定三件事：场景隔离、一次性、开关关闭时不校验也不生成。</p>
 */
@DisplayName("MemberCaptchaService 会员验证码")
class MemberCaptchaServiceTest {

    private static final String CONFIG_KEY = "mem.security.captchaEnabled";

    private ConfigProvider configProvider;
    private MemberCaptchaService service;

    @BeforeEach
    void setUp() {
        configProvider = mock(ConfigProvider.class);
        service = new MemberCaptchaService(configProvider);
        ReflectionTestUtils.setField(service, "masterEnabled", true);
        when(configProvider.getBooleanValue(eq(CONFIG_KEY), anyBoolean())).thenReturn(true);
    }

    private String codeOf(String scene, String key) {
        return SaManager.getSaTokenDao().get("lx:member:captcha:" + scene + ":" + key);
    }

    @Test
    @DisplayName("开启时生成图片与 key，验证码 5 分钟有效")
    void createReturnsImage() {
        MemberCaptchaResponse response = service.create("login");

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getKey()).isNotBlank();
        assertThat(response.getImage()).startsWith("data:image/png;base64,");
        assertThat(response.getExpireIn()).isEqualTo(300);
        assertThat(codeOf("login", response.getKey())).hasSize(4);
    }

    @Test
    @DisplayName("验证通过后立即作废，不能重复使用")
    void verifyIsSingleUse() {
        MemberCaptchaResponse response = service.create("login");
        String code = codeOf("login", response.getKey());

        assertThatCode(() -> service.verify("login", response.getKey(), code)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verify("login", response.getKey(), code))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(ErrorCode.CAPTCHA_INVALID.getCode()));
    }

    @Test
    @DisplayName("场景隔离：注册验证码不能拿去登录")
    void scenesAreIsolated() {
        MemberCaptchaResponse response = service.create("register");
        String code = codeOf("register", response.getKey());

        assertThatThrownBy(() -> service.verify("login", response.getKey(), code))
                .isInstanceOf(ServiceException.class);
        // 登录场景校验失败不应删掉注册场景的验证码
        assertThat(codeOf("register", response.getKey())).isEqualTo(code);
    }

    @Test
    @DisplayName("总闸关闭：不生成验证码，verify 直接放行")
    void masterSwitchOffDisablesEverything() {
        ReflectionTestUtils.setField(service, "masterEnabled", false);

        MemberCaptchaResponse response = service.create("login");

        assertThat(response.isEnabled()).isFalse();
        assertThatCode(() -> service.verify("login", null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("参数关闭：不生成验证码")
    void configSwitchOffDisables() {
        when(configProvider.getBooleanValue(eq(CONFIG_KEY), anyBoolean())).thenReturn(false);

        assertThat(service.create("login").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("非法场景：直接拒绝，避免 Key 前缀被任意拼接")
    void invalidSceneRejected() {
        assertThatThrownBy(() -> service.create("whatever"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("valid.member.captcha.scene"));
    }
}
