package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberChannelTestResult;
import com.lingxiao.modules.member.dto.MemberChannelUpdateRequest;
import com.lingxiao.modules.member.entity.MemChannelConfig;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemChannelConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员消息通道配置测试。
 *
 * <p>覆盖运行期模式选择、停用语义、生产实现缺失时的保护，以及
 * 「测试发送」在开发模式回显验证码、生产模式不回显。</p>
 */
@DisplayName("MemberChannelConfigService 消息通道配置")
class MemberChannelConfigServiceTest {

    private MemChannelConfigMapper configMapper;
    private DevMemberSmsSender devSmsSender;
    private MemberSmsSender prodSmsSender;
    private DevMemberEmailSender devEmailSender;
    private MemberEmailSender prodEmailSender;
    private MemberChannelConfigService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemChannelConfig.class);
    }

    @BeforeEach
    void setUp() {
        configMapper = mock(MemChannelConfigMapper.class);
        devSmsSender = mock(DevMemberSmsSender.class);
        prodSmsSender = mock(MemberSmsSender.class);
        devEmailSender = mock(DevMemberEmailSender.class);
        prodEmailSender = mock(MemberEmailSender.class);
        service = new MemberChannelConfigService(configMapper,
                Arrays.asList(devSmsSender, prodSmsSender),
                Arrays.asList(devEmailSender, prodEmailSender));
    }

    private static MemChannelConfig config(Long id, String type, String mode, String status) {
        MemChannelConfig config = new MemChannelConfig();
        config.setConfigId(id);
        config.setChannelType(type);
        config.setMode(mode);
        config.setProvider("dev");
        config.setStatus(status);
        return config;
    }

    private static MemUser user() {
        MemUser user = new MemUser();
        user.setMemberId(100L);
        user.setUsername("alice");
        return user;
    }

    @Test
    @DisplayName("dev 模式：使用开发日志实现")
    void devModeUsesDevSender() {
        when(configMapper.selectOne(any())).thenReturn(config(1L, "sms", "dev", "0"));
        MemUser user = user();

        boolean sent = service.send(user, MemberChannelConfigService.TYPE_SMS, "13800000000", "123456", 10);

        assertThat(sent).isTrue();
        verify(devSmsSender).sendResetCode(user, "13800000000", "123456", 10);
        verify(prodSmsSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("prod 模式：使用部署方提供的真实实现")
    void prodModeUsesProdSender() {
        when(configMapper.selectOne(any())).thenReturn(config(2L, "email", "prod", "0"));
        MemUser user = user();

        boolean sent = service.send(user, MemberChannelConfigService.TYPE_EMAIL, "alice@example.com", "123456", 10);

        assertThat(sent).isTrue();
        verify(prodEmailSender).sendResetCode(user, "alice@example.com", "123456", 10);
        verify(devEmailSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("通道停用：找回密码静默不下发，不抛异常")
    void disabledChannelSkipsSend() {
        when(configMapper.selectOne(any())).thenReturn(config(1L, "sms", "dev", "1"));

        boolean sent = service.send(user(), MemberChannelConfigService.TYPE_SMS, "13800000000", "123456", 10);

        assertThat(sent).isFalse();
        verify(devSmsSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
        verify(prodSmsSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("缺少生产实现时拒绝切到 prod，避免保存后找回密码全部失败")
    void rejectsProdWhenImplementationMissing() {
        when(configMapper.selectById(1L)).thenReturn(config(1L, "sms", "dev", "0"));
        MemberChannelConfigService devOnly = new MemberChannelConfigService(configMapper,
                Collections.singletonList(devSmsSender),
                Collections.singletonList(devEmailSender));
        MemberChannelUpdateRequest request = new MemberChannelUpdateRequest();
        request.setMode("prod");
        request.setStatus("0");

        assertThatThrownBy(() -> devOnly.update(1L, request))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.channelProdUnavailable"));
        verify(configMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("密钥留空：沿用原值，不把已有配置清掉")
    void blankSecretKeepsExistingValue() {
        MemChannelConfig config = config(1L, "sms", "dev", "0");
        config.setAccessSecret("old-secret");
        when(configMapper.selectById(1L)).thenReturn(config);
        MemberChannelUpdateRequest request = new MemberChannelUpdateRequest();
        request.setMode("dev");
        request.setProvider("aliyun");
        request.setStatus("0");
        request.setAccessSecret("  ");

        service.update(1L, request);

        assertThat(config.getAccessSecret()).isEqualTo("old-secret");
        assertThat(config.getProvider()).isEqualTo("aliyun");
        verify(configMapper).updateById(config);
    }

    @Test
    @DisplayName("测试发送（dev）：回显验证码，方便确认日志通道")
    void testSendReturnsCodeInDevMode() {
        when(configMapper.selectById(1L)).thenReturn(config(1L, "sms", "dev", "0"));

        MemberChannelTestResult result = service.test(1L, " 13800000000 ");

        assertThat(result.getChannelType()).isEqualTo("sms");
        assertThat(result.getMode()).isEqualTo("dev");
        assertThat(result.getTarget()).isEqualTo("13800000000");
        assertThat(result.getCode()).matches("^\\d{6}$");
        verify(devSmsSender).sendResetCode(any(), eq("13800000000"), eq(result.getCode()), eq(10));
    }

    @Test
    @DisplayName("测试发送（prod）：不回显验证码")
    void testSendHidesCodeInProdMode() {
        when(configMapper.selectById(2L)).thenReturn(config(2L, "email", "prod", "0"));

        MemberChannelTestResult result = service.test(2L, "alice@example.com");

        assertThat(result.getCode()).isNull();
        verify(prodEmailSender).sendResetCode(any(), eq("alice@example.com"), anyString(), eq(10));
    }

    @Test
    @DisplayName("测试发送：通道停用时明确报错")
    void testSendRejectsDisabledChannel() {
        when(configMapper.selectById(1L)).thenReturn(config(1L, "sms", "dev", "1"));

        assertThatThrownBy(() -> service.test(1L, "13800000000"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.channelDisabled"));
    }

    @Test
    @DisplayName("测试发送：手机号格式错误直接拒绝")
    void testSendValidatesMobile() {
        when(configMapper.selectById(1L)).thenReturn(config(1L, "sms", "dev", "0"));

        assertThatThrownBy(() -> service.test(1L, "not-a-mobile"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("valid.member.channel.mobile"));
        verify(devSmsSender, never()).sendResetCode(any(), anyString(), anyString(), anyInt());
    }
}
