package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberLoginRequest;
import com.lingxiao.modules.member.dto.MemberLoginResponse;
import com.lingxiao.modules.member.dto.MemberRegisterRequest;
import com.lingxiao.modules.member.dto.MemberResetPasswordRequest;
import com.lingxiao.modules.member.entity.MemLevel;
import com.lingxiao.modules.member.entity.MemPasswordReset;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemPasswordResetMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import com.lingxiao.modules.member.security.MemberPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员账号服务测试。
 *
 * <p>覆盖三条产品红线：</p>
 * <ol>
 *   <li>注册 / 登录与平台后台完全分离，只写 {@code mem_user}；</li>
 *   <li>登录失败计数落库，达到阈值锁定，锁定期内不再比对密码；</li>
 *   <li>找回密码只认最新一条未用且未过期的验证码，用完即作废。</li>
 * </ol>
 */
@DisplayName("MemberUserService 会员账号")
class MemberUserServiceTest {

    private MemUserMapper userMapper;
    private MemPasswordResetMapper passwordResetMapper;
    private MemberLevelService levelService;
    private MemberPasswordPolicy passwordPolicy;
    private MemberPasswordEncoder passwordEncoder;
    private MemberCaptchaService captchaService;
    private MemberTokenIssuer tokenIssuer;
    private MemberResetCodeSender resetCodeSender;
    private MemberRateLimitService rateLimitService;
    private ConfigProvider configProvider;
    private MemberUserService service;

    /**
     * Lambda 条件构造器依赖 MyBatis-Plus 的表元数据缓存；纯单测不会启动 Mapper 扫描，
     * 不初始化就会报 {@code can not find lambda cache for this entity}。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemUser.class);
        TableInfoHelper.initTableInfo(assistant, MemPasswordReset.class);
        TableInfoHelper.initTableInfo(assistant, MemLevel.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(MemUserMapper.class);
        passwordResetMapper = mock(MemPasswordResetMapper.class);
        levelService = mock(MemberLevelService.class);
        passwordPolicy = mock(MemberPasswordPolicy.class);
        passwordEncoder = mock(MemberPasswordEncoder.class);
        captchaService = mock(MemberCaptchaService.class);
        tokenIssuer = mock(MemberTokenIssuer.class);
        resetCodeSender = mock(MemberResetCodeSender.class);
        rateLimitService = mock(MemberRateLimitService.class);
        configProvider = mock(ConfigProvider.class);
        service = new MemberUserService(userMapper, passwordResetMapper, levelService, passwordPolicy,
                passwordEncoder, captchaService, tokenIssuer, resetCodeSender, rateLimitService, configProvider);

        when(configProvider.getIntValue(eq("mem.account.maxRetryCount"), anyInt())).thenReturn(5);
        when(configProvider.getIntValue(eq("mem.account.lockMinutes"), anyInt())).thenReturn(10);
        when(configProvider.getIntValue(eq("mem.register.resetCodeExpireMinutes"), anyInt())).thenReturn(10);
        when(configProvider.getIntValue(eq("mem.register.rateLimitCount"), anyInt())).thenReturn(5);
        when(configProvider.getIntValue(eq("mem.register.rateLimitMinutes"), anyInt())).thenReturn(10);
        when(configProvider.getIntValue(eq("mem.account.loginRateLimitCount"), anyInt())).thenReturn(30);
        when(configProvider.getIntValue(eq("mem.account.loginRateLimitMinutes"), anyInt())).thenReturn(10);
        when(configProvider.getIntValue(eq("mem.register.resetRateLimitCount"), anyInt())).thenReturn(5);
        when(configProvider.getIntValue(eq("mem.register.resetRateLimitMinutes"), anyInt())).thenReturn(10);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(levelService.defaultLevel()).thenReturn(level(1L, "注册会员"));
        when(levelService.nameOf(anyLong())).thenAnswer(inv -> "注册会员");
    }

    private static MemLevel level(Long id, String name) {
        MemLevel level = new MemLevel();
        level.setLevelId(id);
        level.setLevelName(name);
        return level;
    }

    private static MemUser user() {
        MemUser user = new MemUser();
        user.setMemberId(100L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setPassword("encoded");
        user.setStatus("0");
        user.setLevelId(1L);
        user.setPoints(0);
        user.setGrowth(0);
        user.setLoginFailCount(0);
        return user;
    }

    private static MemberRegisterRequest registerRequest() {
        MemberRegisterRequest request = new MemberRegisterRequest();
        request.setUsername("alice");
        request.setPassword("abc12345");
        request.setNickname("Alice");
        request.setAgreement(true);
        return request;
    }

    private static MemberLoginRequest loginRequest() {
        MemberLoginRequest request = new MemberLoginRequest();
        request.setAccount("alice");
        request.setPassword("abc12345");
        return request;
    }

    @Test
    @DisplayName("注册成功：落 mem_user，默认等级与渠道，注册后直接发 Token")
    void registerSuccess() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(MemUser.class))).thenAnswer(inv -> {
            ((MemUser) inv.getArgument(0)).setMemberId(100L);
            return 1;
        });
        MemberLoginResponse expected = new MemberLoginResponse();
        expected.setMemberId(100L);
        when(tokenIssuer.issue(any(MemUser.class), anyString())).thenReturn(expected);

        MemberLoginResponse response = service.register(registerRequest());

        assertThat(response.getMemberId()).isEqualTo(100L);
        ArgumentCaptor<MemUser> captor = ArgumentCaptor.forClass(MemUser.class);
        verify(userMapper).insert(captor.capture());
        MemUser saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getLevelId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo("0");
        assertThat(saved.getRegisterChannel()).isEqualTo("web");
        assertThat(saved.getLoginFailCount()).isZero();
        verify(captchaService).verify(eq("register"), any(), any());
    }

    @Test
    @DisplayName("注册未勾选协议：拒绝且不落库")
    void registerRequiresAgreement() {
        MemberRegisterRequest request = registerRequest();
        request.setAgreement(false);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.agreement"));
        verify(userMapper, never()).insert(any(MemUser.class));
    }

    @Test
    @DisplayName("注册用户名已存在：提示具体字段，不落库")
    void registerRejectsDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.usernameExists"));
        verify(userMapper, never()).insert(any(MemUser.class));
    }

    @Test
    @DisplayName("登录成功：清零失败计数并刷新登录信息")
    void loginSuccess() {
        MemUser user = user();
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user));
        when(passwordEncoder.matches("abc12345", "encoded")).thenReturn(true);
        MemberLoginResponse expected = new MemberLoginResponse();
        when(tokenIssuer.issue(any(MemUser.class), anyString())).thenReturn(expected);

        MemberLoginResponse response = service.login(loginRequest());

        assertThat(response).isSameAs(expected);
        verify(userMapper).update(isNull(), any());
        verify(tokenIssuer).issue(user, "注册会员");
    }

    @Test
    @DisplayName("密码错误：累计失败次数，未到阈值返回 2001")
    void loginWrongPasswordIncrementsFailCount() {
        MemUser user = user();
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(ErrorCode.LOGIN_FAILED.getCode()));
        verify(userMapper).update(isNull(), any());
        verify(tokenIssuer, never()).issue(any(MemUser.class), anyString());
    }

    @Test
    @DisplayName("第 5 次密码错误：锁定账号，错误码 2004")
    void loginLocksAfterMaxRetries() {
        MemUser user = user();
        user.setLoginFailCount(4);
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> {
                    ServiceException ex = (ServiceException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode());
                    assertThat(ex.getMessageKey()).isEqualTo("error.2004.failures");
                });
    }

    @Test
    @DisplayName("锁定期内：不再比对密码，直接拒绝")
    void loginRejectedWhileLocked() {
        MemUser user = user();
        user.setLockTime(LocalDateTime.now().plusMinutes(5));
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user));

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode()));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("找回密码：账号不存在也静默成功，不落验证码")
    void forgotPasswordSilentForUnknownAccount() {
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.forgotPassword("nobody");

        verify(passwordResetMapper, never()).insert(any(MemPasswordReset.class));
        verify(resetCodeSender, never()).send(any(MemUser.class), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("找回密码：作废旧码并下发 6 位新码")
    void forgotPasswordIssuesCode() {
        MemUser user = user();
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user));

        service.forgotPassword("alice");

        verify(passwordResetMapper).update(isNull(), any());
        ArgumentCaptor<MemPasswordReset> captor = ArgumentCaptor.forClass(MemPasswordReset.class);
        verify(passwordResetMapper).insert(captor.capture());
        assertThat(captor.getValue().getUsed()).isEqualTo("0");
        assertThat(captor.getValue().getExpireTime()).isAfter(LocalDateTime.now());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(resetCodeSender).send(eq(user), eq("alice"), code.capture(), eq(10));
        assertThat(code.getValue()).hasSize(6).containsOnlyDigits();
    }

    @Test
    @DisplayName("验证码重置：校验通过后作废验证码并踢掉全部会话")
    void resetPasswordConsumesCode() {
        MemUser user = user();
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user));
        MemPasswordReset reset = new MemPasswordReset();
        reset.setResetId(900L);
        reset.setMemberId(100L);
        reset.setCodeHash("codeHash");
        reset.setUsed("0");
        reset.setExpireTime(LocalDateTime.now().plusMinutes(5));
        when(passwordResetMapper.selectList(any())).thenReturn(Collections.singletonList(reset));
        when(passwordEncoder.matches("123456", "codeHash")).thenReturn(true);

        MemberResetPasswordRequest request = new MemberResetPasswordRequest();
        request.setAccount("alice");
        request.setCode("123456");
        request.setNewPassword("newpass123");
        service.resetPassword(request);

        verify(passwordPolicy).validate("newpass123");
        verify(passwordResetMapper).update(isNull(), any());
        verify(tokenIssuer).logoutAll(100L);
    }

    @Test
    @DisplayName("验证码重置：过期验证码拒绝")
    void resetPasswordRejectsExpiredCode() {
        MemUser user = user();
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user));
        MemPasswordReset reset = new MemPasswordReset();
        reset.setResetId(900L);
        reset.setCodeHash("codeHash");
        reset.setUsed("0");
        reset.setExpireTime(LocalDateTime.now().minusMinutes(1));
        when(passwordResetMapper.selectList(any())).thenReturn(Collections.singletonList(reset));

        MemberResetPasswordRequest request = new MemberResetPasswordRequest();
        request.setAccount("alice");
        request.setCode("123456");
        request.setNewPassword("newpass123");

        assertThatThrownBy(() -> service.resetPassword(request))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getMessageKey())
                        .isEqualTo("error.1001.member.resetCodeExpired"));
        verify(tokenIssuer, never()).logoutAll(anyLong());
    }

    @Test
    @DisplayName("后台列表：一次查询补齐等级名")
    void pageFillsLevelName() {
        MemUser user = user();
        Page<MemUser> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(Collections.singletonList(user));
        when(userMapper.selectPage(any(), any())).thenReturn(page);
        Map<Long, String> names = new HashMap<>();
        names.put(1L, "注册会员");
        when(levelService.nameMap()).thenReturn(names);

        assertThat(service.page(1, 20, null, null, null).getRecords().get(0).getLevelName())
                .isEqualTo("注册会员");
    }

    @Test
    @DisplayName("后台停用会员：立即注销该会员全部会话")
    void changeStatusToDisabledLogsOut() {
        when(userMapper.selectById(100L)).thenReturn(user());

        service.changeStatus(100L, "1");

        verify(userMapper).update(isNull(), any());
        verify(tokenIssuer).logoutAll(100L);
    }

    @Test
    @DisplayName("后台启用会员：不踢会话")
    void changeStatusToEnabledKeepsSessions() {
        when(userMapper.selectById(100L)).thenReturn(user());

        service.changeStatus(100L, "0");

        verify(tokenIssuer, never()).logoutAll(anyLong());
    }

    @Test
    @DisplayName("后台删除会员：逻辑删除并踢掉全部会话")
    void deleteLogsOut() {
        when(userMapper.selectById(100L)).thenReturn(user());

        service.delete(100L);

        verify(userMapper).deleteById(100L);
        verify(tokenIssuer).logoutAll(100L);
    }

    @Test
    @DisplayName("注册限流：先按用户名与 IP 各记一次，再校验验证码")
    void registerAppliesRateLimit() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(levelService.defaultLevel()).thenReturn(level(1L, "注册会员"));
        when(tokenIssuer.issue(any(MemUser.class), anyString())).thenReturn(new MemberLoginResponse());

        service.register(registerRequest());

        verify(rateLimitService).check("member:register:id:alice", 5, 10);
        verify(rateLimitService).check("member:register:ip:unknown", 50, 10);
    }

    @Test
    @DisplayName("登录限流：按账号与 IP 各记一次")
    void loginAppliesRateLimit() {
        MemUser user = user();
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenIssuer.issue(any(MemUser.class), anyString())).thenReturn(new MemberLoginResponse());

        service.login(loginRequest());

        verify(rateLimitService).check("member:login:id:alice", 30, 10);
        verify(rateLimitService).check("member:login:ip:unknown", 300, 10);
    }

    @Test
    @DisplayName("找回密码限流：账号不存在也计数，避免枚举")
    void forgotPasswordAppliesRateLimit() {
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.forgotPassword("nobody");

        verify(rateLimitService).check("member:forgot:id:nobody", 5, 10);
        verify(rateLimitService).check("member:forgot:ip:unknown", 50, 10);
    }

    @Test
    @DisplayName("重置密码限流：与找回密码动作分开计数")
    void resetPasswordAppliesRateLimit() {
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());
        MemberResetPasswordRequest request = new MemberResetPasswordRequest();
        request.setAccount("alice");
        request.setCode("123456");
        request.setNewPassword("newpass123");

        assertThatThrownBy(() -> service.resetPassword(request))
                .isInstanceOf(ServiceException.class);

        verify(rateLimitService).check("member:reset:id:alice", 5, 10);
        verify(rateLimitService).check("member:reset:ip:unknown", 50, 10);
    }
}
