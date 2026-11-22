package com.lingxiao.modules.member.service;

import com.lingxiao.modules.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 第三方头像本地化安全边界测试。
 *
 * <p>头像 URL 来自第三方平台返回值，属于外部输入。这里锁定 SSRF 防护：
 * 只允许公网 http(s)，内网 / 回环 / 链路本地 / 带 userInfo 的地址都在发起
 * 请求前就被拒绝；文件模块未启用时也不影响登录主流程。</p>
 */
@DisplayName("MemberAvatarLocalizer 头像下载安全边界")
class MemberAvatarLocalizerTest {

    @SuppressWarnings("unchecked")
    private MemberAvatarLocalizer localizer() {
        ObjectProvider<FileService> provider = mock(ObjectProvider.class);
        return new MemberAvatarLocalizer(provider);
    }

    @Test
    @DisplayName("内网 / 回环 / 非法协议的头像地址直接忽略")
    void rejectsPrivateAndUnsupportedTargets() {
        MemberAvatarLocalizer localizer = localizer();

        assertThat(localizer.localize("github", "http://127.0.0.1/avatar.png")).isNull();
        assertThat(localizer.localize("github", "http://localhost/avatar.png")).isNull();
        assertThat(localizer.localize("github", "http://10.0.0.1/avatar.png")).isNull();
        assertThat(localizer.localize("github", "http://192.168.1.2/avatar.png")).isNull();
        assertThat(localizer.localize("github", "http://169.254.169.254/avatar.png")).isNull();
        assertThat(localizer.localize("github", "http://[::1]/avatar.png")).isNull();
        assertThat(localizer.localize("github", "file:///etc/passwd")).isNull();
        assertThat(localizer.localize("github", "http://user:pass@example.com/avatar.png")).isNull();
    }

    @Test
    @DisplayName("空地址不下载，直接返回 null")
    void ignoresBlankUrl() {
        MemberAvatarLocalizer localizer = localizer();

        assertThat(localizer.localize("github", null)).isNull();
        assertThat(localizer.localize("github", "   ")).isNull();
    }
}
