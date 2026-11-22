package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberContactChangeRequest;
import com.lingxiao.modules.member.dto.MemberContactCodeRequest;
import com.lingxiao.modules.member.entity.MemContactChange;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemContactChangeMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import com.lingxiao.modules.member.security.MemberPasswordEncoder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员手机号 / 邮箱换绑测试。
 *
 * <p>重点覆盖：已设置密码必须校验密码、新值唯一性、验证码发送目标、
 * 验证码一次性与过期语义、数据库唯一键冲突的友好提示。</p>
 */
@DisplayName("MemberContactService 手机号 / 邮箱换绑")
class MemberContactServiceTest {

    private MemUserMapper userMapper;
    private MemContactChangeMapper changeMapper;
    private MemberPasswordEncoder passwordEncoder;
    private MemberChannelConfigService channelConfigService;
    private MemberRateLimitService rateLimitService;
    private ConfigProvider configProvider;
    private MemberContactService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemUser.class);
        TableInfoHelper.initTableInfo(assistant, MemContactChange.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(MemUserMapper.class);
        changeMapper = mock(MemContactChangeMapper.class);
        passwordEncoder = mock(MemberPasswordEncoder.class);
        channelConfigService = mock(MemberChannelConfigService.class);
        rateLimitService = mock(MemberRateLimitService.class);
        configProvider = mock(ConfigProvider.class);
        service = new MemberContactService(userMapper, changeMapper, passwordEncoder,
                channelConfigService, rateLimitService, configProvider);

        when(configProvider.getIntValue(eq("mem.account.contactCodeExpireMinutes"), anyInt())).thenReturn(10);
        when(configProvider.getIntValue(eq("mem.account.contactRateLimitCount"), anyInt())).thenReturn(5);
        when(configProvider.getIntValue(eq("mem.account.contactRateLimitMinutes"), anyInt())).thenReturn(10);
        when(passwordEncoder.encode(anyString())).thenReturn("code-hash");
    }

    private static MemUser user(String passwordSet) {
        MemUser user = new MemUser();
        user.setMemberId(100L);
        user.setTenantId(0L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setPassword("encoded");
        user.setPasswordSet(passwordSet);
        user.setStatus("0");
        return user;
    }

    private static MemberContactCodeRequest codeRequest(String type, String value, String password) {
        MemberContactCodeRequest request = new MemberContactCodeRequest();
        request.setType(type);
        request.setValue(value);
        request.setPassword(password);
        return request;
    }

    private static MemberContactChangeRequest changeRequest(String type, String value, String code) {
        MemberContactChangeRequest request = new MemberContactChangeRequest();
        request.setType(type);
        request.setValue(value);
        request.setCode(code);
        return request;
    }

    private static MemContactChange change(String type, String newValue, String codeHash,
                                          LocalDateTime expireTime) {
        MemContactChange change = new MemContactChange();
        change.setChangeId(900L);
        change.setMemberId(100L);
        change.setContactType(type);
        change.setNewValue(newValue);
        change.setCodeHash(codeHash);
        change.setUsed("0");
        change.setExpireTime(expireTime);
        change.setCreateTime(LocalDateTime.now());
        return change;
    }

    @Test
    @DisplayName("已设置密码：发码必须提交当前密码")
    void sendCodeRequiresPasswordWhenSet() {
        when(userMapper.selectById(100L)).thenReturn(user("1"));

        assertThatThrownBy(() -> service.sendCode(100L, codeRequest("email", "new@example.com", null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("valid.member.contact.passwordRequired"));

        verify(changeMapper, never()).insert(any(MemContactChange.class));
        verify(channelConfigService, never()).send(any(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("已设置密码：当前密码错误直接拒绝")
    void sendCodeRejectsWrongPassword() {
        when(userMapper.selectById(100L)).thenReturn(user("1"));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.sendCode(100L, codeRequest("email", "new@example.com", "wrong")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(ErrorCode.OLD_PASSWORD_ERROR.getCode()));

        verify(changeMapper, never()).insert(any(MemContactChange.class));
        verify(channelConfigService, never()).send(any(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("发码成功：作废旧码、发送到新邮箱并只存哈希")
    void sendCodeStoresHashAndSendsToNewEmail() {
        MemUser user = user("1");
        when(userMapper.selectById(100L)).thenReturn(user);
        when(passwordEncoder.matches("pass1234", "encoded")).thenReturn(true);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(channelConfigService.send(eq(user), eq(MemberChannelConfigService.TYPE_EMAIL),
                eq("new@example.com"), anyString(), eq(10))).thenReturn(true);

        service.sendCode(100L, codeRequest("email", "  NEW@Example.com  ", "pass1234"));

        ArgumentCaptor<MemContactChange> row = ArgumentCaptor.forClass(MemContactChange.class);
        verify(changeMapper).insert(row.capture());
        assertThat(row.getValue().getContactType()).isEqualTo("email");
        assertThat(row.getValue().getNewValue()).isEqualTo("new@example.com");
        assertThat(row.getValue().getUsed()).isEqualTo("0");
        assertThat(row.getValue().getExpireTime()).isAfter(LocalDateTime.now());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(channelConfigService).send(eq(user), eq(MemberChannelConfigService.TYPE_EMAIL),
                eq("new@example.com"), code.capture(), eq(10));
        assertThat(code.getValue()).hasSize(6).containsOnlyDigits();
        verify(passwordEncoder).encode(code.getValue());

        // 先作废旧码，再插入新码
        verify(changeMapper, times(1)).update(isNull(), any());
        verify(rateLimitService).check("member:contact:send:id:100", 5, 10);
        verify(rateLimitService).check("member:contact:send:ip:unknown", 50, 10);
    }

    @Test
    @DisplayName("第三方建号未设置密码：发码不要求密码")
    void sendCodeSkipsPasswordWhenNotSet() {
        MemUser user = user("0");
        when(userMapper.selectById(100L)).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(channelConfigService.send(eq(user), eq(MemberChannelConfigService.TYPE_SMS),
                eq("13800138000"), anyString(), eq(10))).thenReturn(true);

        service.sendCode(100L, codeRequest("mobile", "13800138000", null));

        verify(channelConfigService).send(eq(user), eq(MemberChannelConfigService.TYPE_SMS),
                eq("13800138000"), anyString(), eq(10));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("手机号已存在：拒绝且不发码")
    void sendCodeRejectsDuplicateMobile() {
        when(userMapper.selectById(100L)).thenReturn(user("0"));
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.sendCode(100L, codeRequest("mobile", "13800138000", null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.mobileExists"));

        verify(changeMapper, never()).insert(any(MemContactChange.class));
        verify(channelConfigService, never()).send(any(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("通道停用：作废刚写入的验证码并明确报错")
    void sendCodeFailsWhenChannelUnavailable() {
        MemUser user = user("0");
        when(userMapper.selectById(100L)).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(channelConfigService.send(any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.sendCode(100L, codeRequest("mobile", "13800138000", null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.contactChannelUnavailable"));

        verify(changeMapper).insert(any(MemContactChange.class));
        verify(changeMapper, times(2)).update(isNull(), any());
    }

    @Test
    @DisplayName("通道抛异常：同样作废验证码并透传原始异常")
    void sendCodeInvalidatesCodeWhenChannelThrows() {
        MemUser user = user("0");
        when(userMapper.selectById(100L)).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);
        IllegalStateException boom = new IllegalStateException("smtp down");
        when(channelConfigService.send(any(), anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(boom);

        assertThatThrownBy(() -> service.sendCode(100L, codeRequest("email", "new@example.com", null)))
                .isSameAs(boom);

        verify(changeMapper).insert(any(MemContactChange.class));
        // 第一次作废旧码，第二次作废刚写入的新码
        verify(changeMapper, times(2)).update(isNull(), any());
    }

    @Test
    @DisplayName("换绑成功：更新 mem_user 并一次性作废验证码")
    void changeUpdatesUserAndConsumesCode() {
        MemUser user = user("0");
        when(userMapper.selectById(100L)).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);
        MemContactChange change = change("email", "new@example.com", "code-hash",
                LocalDateTime.now().plusMinutes(5));
        when(changeMapper.selectList(any())).thenReturn(Collections.singletonList(change));
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);

        service.change(100L, changeRequest("email", "NEW@example.com", "123456"));

        verify(userMapper).update(isNull(), any());
        verify(changeMapper).update(isNull(), any());
        verify(rateLimitService).check("member:contact:change:id:100", 5, 10);
    }

    @Test
    @DisplayName("验证码过期：拒绝换绑")
    void changeRejectsExpiredCode() {
        when(userMapper.selectById(100L)).thenReturn(user("0"));
        when(userMapper.selectCount(any())).thenReturn(0L);
        MemContactChange change = change("email", "new@example.com", "code-hash",
                LocalDateTime.now().minusMinutes(1));
        when(changeMapper.selectList(any())).thenReturn(Collections.singletonList(change));

        assertThatThrownBy(() -> service.change(100L, changeRequest("email", "new@example.com", "123456")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.contactCodeExpired"));

        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("提交值与发码目标不一致：拒绝，避免把码用在新值上")
    void changeRejectsMismatchedValue() {
        when(userMapper.selectById(100L)).thenReturn(user("0"));
        when(userMapper.selectCount(any())).thenReturn(0L);
        MemContactChange change = change("email", "other@example.com", "code-hash",
                LocalDateTime.now().plusMinutes(5));
        when(changeMapper.selectList(any())).thenReturn(Collections.singletonList(change));

        assertThatThrownBy(() -> service.change(100L, changeRequest("email", "new@example.com", "123456")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.contactCodeMismatch"));

        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("验证码错误：拒绝换绑")
    void changeRejectsWrongCode() {
        when(userMapper.selectById(100L)).thenReturn(user("0"));
        when(userMapper.selectCount(any())).thenReturn(0L);
        MemContactChange change = change("email", "new@example.com", "code-hash",
                LocalDateTime.now().plusMinutes(5));
        when(changeMapper.selectList(any())).thenReturn(Collections.singletonList(change));
        when(passwordEncoder.matches("000000", "code-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.change(100L, changeRequest("email", "new@example.com", "000000")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.contactCodeInvalid"));

        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("并发下撞唯一键：翻成手机号已存在")
    void changeMapsDuplicateKey() {
        when(userMapper.selectById(100L)).thenReturn(user("0"));
        when(userMapper.selectCount(any())).thenReturn(0L);
        MemContactChange change = change("mobile", "13800138000", "code-hash",
                LocalDateTime.now().plusMinutes(5));
        when(changeMapper.selectList(any())).thenReturn(Collections.singletonList(change));
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);
        when(userMapper.update(isNull(), any())).thenThrow(new org.springframework.dao.DuplicateKeyException(
                "Duplicate entry for key 'uk_mem_user_mobile'"));

        assertThatThrownBy(() -> service.change(100L, changeRequest("mobile", "13800138000", "123456")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.mobileExists"));
    }

    @Test
    @DisplayName("非法手机号 / 邮箱格式：在发码前拒绝")
    void sendCodeRejectsInvalidFormat() {
        when(userMapper.selectById(100L)).thenReturn(user("0"));

        assertThatThrownBy(() -> service.sendCode(100L, codeRequest("mobile", "not-a-mobile", null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("valid.member.contact.mobile"));

        assertThatThrownBy(() -> service.sendCode(100L, codeRequest("email", "not-an-email", null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("valid.member.contact.email"));

        verify(changeMapper, never()).insert(any(MemContactChange.class));
    }
}
