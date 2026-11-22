package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingxiao.modules.member.entity.MemLevel;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.entity.MemUserSocial;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import com.lingxiao.modules.member.mapper.MemUserSocialMapper;
import com.lingxiao.modules.member.oauth.OAuthUserInfo;
import com.lingxiao.modules.member.security.MemberPasswordEncoder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第三方登录账号决策测试。
 *
 * <p>锁定两条安全规则：</p>
 * <ol>
 *   <li>只使用平台明确标记为已验证的邮箱 / 手机号匹配已有会员；</li>
 *   <li>命中已有会员时不自动登录，返回“需要先绑定原账号”。</li>
 * </ol>
 */
@DisplayName("MemberOAuthAccountService 第三方建号与匹配")
class MemberOAuthAccountServiceTest {

    private MemUserMapper userMapper;
    private MemUserSocialMapper socialMapper;
    private MemberLevelService levelService;
    private MemberPasswordEncoder passwordEncoder;
    private MemberOAuthConfigService configService;
    private MemberAvatarLocalizer avatarLocalizer;
    private MemberOAuthAccountService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemUser.class);
        TableInfoHelper.initTableInfo(assistant, MemUserSocial.class);
        TableInfoHelper.initTableInfo(assistant, MemLevel.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(MemUserMapper.class);
        socialMapper = mock(MemUserSocialMapper.class);
        levelService = mock(MemberLevelService.class);
        passwordEncoder = mock(MemberPasswordEncoder.class);
        configService = mock(MemberOAuthConfigService.class);
        avatarLocalizer = mock(MemberAvatarLocalizer.class);
        service = new MemberOAuthAccountService(userMapper, socialMapper, levelService,
                passwordEncoder, configService, avatarLocalizer);

        when(socialMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(levelService.defaultLevel()).thenReturn(level());
        when(avatarLocalizer.localize(any(), any())).thenReturn(null);
    }

    private static MemLevel level() {
        MemLevel level = new MemLevel();
        level.setLevelId(1L);
        level.setLevelName("注册会员");
        return level;
    }

    private static OAuthUserInfo info(boolean emailVerified) {
        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider("github");
        info.setOpenId("open-1");
        info.setNickname("octocat");
        info.setEmail("a@b.com");
        info.setEmailVerified(emailVerified);
        return info;
    }

    @Test
    @DisplayName("已验证邮箱命中已有会员时要求先绑定，不自动建号")
    void verifiedEmailRequiresBinding() {
        MemUser existing = new MemUser();
        existing.setMemberId(9L);
        existing.setUsername("existing");
        when(userMapper.selectList(any())).thenReturn(Collections.singletonList(existing));

        MemberOAuthLoginOutcome outcome = service.loginOrRegister(info(true), true);

        assertThat(outcome.requiresBinding()).isTrue();
        assertThat(outcome.getExistingMember().getMemberId()).isEqualTo(9L);
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("未验证邮箱不参与匹配，直接自动建号且不写邮箱")
    void unverifiedEmailIsIgnored() {
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());

        MemberOAuthLoginOutcome outcome = service.loginOrRegister(info(false), true);

        assertThat(outcome.requiresBinding()).isFalse();
        MemUser created = outcome.getMember();
        assertThat(created).isNotNull();
        assertThat(created.getPasswordSet()).isEqualTo("0");
        assertThat(created.getEmail()).isNull();
        verify(socialMapper).insert(any(MemUserSocial.class));
    }

    @Test
    @DisplayName("已验证邮箱无冲突时写入新会员，并占用唯一键")
    void verifiedEmailIsStored() {
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());

        MemberOAuthLoginOutcome outcome = service.loginOrRegister(info(true), true);

        assertThat(outcome.requiresBinding()).isFalse();
        assertThat(outcome.getMember().getEmail()).isEqualTo("a@b.com");

        ArgumentCaptor<MemUserSocial> captor = ArgumentCaptor.forClass(MemUserSocial.class);
        verify(socialMapper).insert(captor.capture());
        assertThat(captor.getValue().getEmailVerified()).isEqualTo("1");
        assertThat(captor.getValue().getEmail()).isEqualTo("a@b.com");
    }
}
