package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.web.util.ServletUtils;
import com.lingxiao.modules.member.dto.MemberOAuthBindingVO;
import com.lingxiao.modules.member.entity.MemLevel;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.entity.MemUserSocial;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import com.lingxiao.modules.member.mapper.MemUserSocialMapper;
import com.lingxiao.modules.member.oauth.OAuthUserInfo;
import com.lingxiao.modules.member.security.MemberPasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 第三方账号与会员账号的绑定 / 自动建号。
 *
 * <p>只写 {@code mem_user} 与 {@code mem_user_social}，不触碰任何 {@code sys_*}
 * 表；第三方登录永远不会获得后台权限。</p>
 *
 * <p><b>账号唯一性：</b>只使用平台明确标记为“已验证”的邮箱 / 手机号参与匹配与
 * 落库，未验证的废值直接丢弃。第三方没有可用联系方式时留空，不抢占唯一键。</p>
 *
 * <p><b>同邮箱 / 手机号：</b>不做静默合并。命中已有会员时不自动登录，返回
 * {@link MemberOAuthLoginOutcome#requiresBinding(MemUser)}，由门户要求用户先登录
 * 原账号再完成绑定，避免第三方冒用邮箱接管账号。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberOAuthAccountService {

    private static final int USERNAME_MAX = 64;
    private static final int NICKNAME_MAX = 64;
    private static final int AVATAR_MAX = 255;
    private static final int EMAIL_MAX = 128;
    private static final int MOBILE_MAX = 32;
    private static final int INSERT_ATTEMPTS = 3;
    private static final String PASSWORD_SET_NO = "0";
    private static final String VERIFIED_YES = "1";
    private static final String VERIFIED_NO = "0";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemUserMapper userMapper;
    private final MemUserSocialMapper socialMapper;
    private final MemberLevelService levelService;
    private final MemberPasswordEncoder passwordEncoder;
    private final MemberOAuthConfigService configService;
    private final MemberAvatarLocalizer avatarLocalizer;

    /**
     * 登录或自动建号。
     *
     * @param autoRegister 关闭时，未绑定的第三方账号直接报“请先登录后绑定”
     * @return 已登录会员，或需要先绑定原账号的决策结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberOAuthLoginOutcome loginOrRegister(OAuthUserInfo info, boolean autoRegister) {
        MemUserSocial social = findByOpenId(info.getProvider(), info.getOpenId());
        if (social != null) {
            MemUser user = userMapper.selectById(social.getMemberId());
            if (user == null) {
                // 会员被删除但绑定记录残留：清掉绑定，按未绑定处理
                socialMapper.deleteById(social.getSocialId());
                social = null;
            } else {
                if (!CommonConstants.STATUS_NORMAL.equals(user.getStatus())) {
                    throw new ServiceException(ErrorCode.ACCOUNT_DISABLED);
                }
                touch(social, info);
                return MemberOAuthLoginOutcome.loggedIn(user);
            }
        }

        if (!autoRegister) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthNotBound");
        }

        MemUser existing = findByVerifiedContact(info);
        if (existing != null) {
            log.info("第三方登录命中已有会员，要求先绑定: provider={}, targetMemberId={}",
                    info.getProvider(), existing.getMemberId());
            return MemberOAuthLoginOutcome.requiresBinding(existing);
        }

        MemberOAuthLoginOutcome created = createMember(info);
        if (created.requiresBinding()) {
            return created;
        }
        MemUser user = created.getMember();
        insertSocial(user.getMemberId(), info);
        log.info("第三方登录自动创建会员: provider={}, memberId={}", info.getProvider(), user.getMemberId());
        return created;
    }

    /** 已登录会员绑定新平台；同一平台只允许一个绑定。 */
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long memberId, OAuthUserInfo info) {
        MemUser user = requireActiveMember(memberId);
        MemUserSocial existing = findByOpenId(info.getProvider(), info.getOpenId());
        if (existing != null) {
            if (memberId.equals(existing.getMemberId())) {
                touch(existing, info);
                return;
            }
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthBindConflict");
        }
        Long count = socialMapper.selectCount(new LambdaQueryWrapper<MemUserSocial>()
                .eq(MemUserSocial::getMemberId, memberId)
                .eq(MemUserSocial::getProvider, info.getProvider()));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthAlreadyBound");
        }
        insertSocial(user.getMemberId(), info);
        log.info("会员绑定第三方账号: provider={}, memberId={}", info.getProvider(), memberId);
    }

    public List<MemberOAuthBindingVO> bindings(Long memberId) {
        List<MemUserSocial> rows = socialMapper.selectList(new LambdaQueryWrapper<MemUserSocial>()
                .eq(MemUserSocial::getMemberId, memberId)
                .orderByAsc(MemUserSocial::getCreateTime));
        List<MemberOAuthBindingVO> result = new ArrayList<>();
        for (MemUserSocial row : rows) {
            MemberOAuthBindingVO vo = new MemberOAuthBindingVO();
            vo.setProvider(row.getProvider());
            vo.setDisplayName(configService.displayName(row.getProvider()));
            vo.setNickname(row.getNickname());
            vo.setAvatar(row.getAvatar());
            vo.setLastLoginTime(row.getLastLoginTime());
            result.add(vo);
        }
        return result;
    }

    /**
     * 解绑。
     *
     * <p>记录不存在也视为成功，避免重复点击报错。若会员从未设置密码，且这是
     * 最后一种登录方式，禁止解绑，否则用户会把自己锁在门外。</p>
     */
    public void unbind(Long memberId, String provider) {
        MemUser user = requireActiveMember(memberId);
        if (PASSWORD_SET_NO.equals(user.getPasswordSet())) {
            Long count = socialMapper.selectCount(new LambdaQueryWrapper<MemUserSocial>()
                    .eq(MemUserSocial::getMemberId, memberId));
            if (count != null && count <= 1) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST,
                        "error.1001.member.oauthLastLoginMethod");
            }
        }
        socialMapper.delete(new LambdaQueryWrapper<MemUserSocial>()
                .eq(MemUserSocial::getMemberId, memberId)
                .eq(MemUserSocial::getProvider, provider));
        log.info("会员解绑第三方账号: provider={}, memberId={}", provider, memberId);
    }

    public MemUser requireActiveMember(Long memberId) {
        MemUser user = memberId == null ? null : userMapper.selectById(memberId);
        if (user == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member");
        }
        if (!CommonConstants.STATUS_NORMAL.equals(user.getStatus())) {
            throw new ServiceException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    private MemUserSocial findByOpenId(String provider, String openId) {
        List<MemUserSocial> rows = socialMapper.selectList(new LambdaQueryWrapper<MemUserSocial>()
                .eq(MemUserSocial::getProvider, provider)
                .eq(MemUserSocial::getOpenId, openId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 只用平台明确标记已验证的联系方式匹配已有会员；未验证的一律忽略。
     * 邮箱优先于手机号，两者命中不同会员时以邮箱为准（极少见，交给后台合并）。
     */
    private MemUser findByVerifiedContact(OAuthUserInfo info) {
        String email = verifiedEmail(info);
        if (email != null) {
            MemUser user = first(userMapper.selectList(new LambdaQueryWrapper<MemUser>()
                    .eq(MemUser::getEmail, email)));
            if (user != null) {
                return user;
            }
        }
        String mobile = verifiedMobile(info);
        if (mobile != null) {
            return first(userMapper.selectList(new LambdaQueryWrapper<MemUser>()
                    .eq(MemUser::getMobile, mobile)));
        }
        return null;
    }

    private MemberOAuthLoginOutcome createMember(OAuthUserInfo info) {
        MemLevel level = levelService.defaultLevel();
        String email = verifiedEmail(info);
        String mobile = verifiedMobile(info);
        String avatarFileRef = resolveAvatar(info);

        DuplicateKeyException lastConflict = null;
        for (int attempt = 0; attempt < INSERT_ATTEMPTS; attempt++) {
            LocalDateTime now = LocalDateTime.now();
            MemUser user = new MemUser();
            user.setTenantId(0L);
            user.setUsername(generateUsername(info, attempt));
            user.setNickname(normalizeNickname(info));
            // 第三方账号没有可用密码；随机值只用于占位，登录路径不会比对它
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setPasswordSet(PASSWORD_SET_NO);
            user.setEmail(email);
            user.setMobile(mobile);
            user.setAvatar(avatarFileRef);
            user.setSex("2");
            user.setLevelId(level.getLevelId());
            user.setPoints(0);
            user.setGrowth(0);
            user.setStatus(CommonConstants.STATUS_NORMAL);
            user.setRegisterChannel("oauth");
            user.setRegisterIp(ServletUtils.getClientIp());
            user.setLoginFailCount(0);
            user.setLastActiveTime(now);
            try {
                userMapper.insert(user);
                return MemberOAuthLoginOutcome.loggedIn(user);
            } catch (DuplicateKeyException e) {
                lastConflict = e;
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("uk_mem_user_username")) {
                    // 用户名并发撞车：换一个随机后缀重试
                    log.info("第三方建号用户名冲突，重试: provider={}", info.getProvider());
                    continue;
                }
                if (message.contains("uk_mem_user_email") || message.contains("uk_mem_user_mobile")) {
                    // 联系方式在查询后被别人占用：不建号，转绑定流程
                    MemUser existing = findByVerifiedContact(info);
                    if (existing != null) {
                        log.info("第三方建号时联系方式并发冲突，转绑定: provider={}", info.getProvider());
                        return MemberOAuthLoginOutcome.requiresBinding(existing);
                    }
                }
                throw e;
            }
        }
        throw lastConflict == null
                ? ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.usernameFormat")
                : lastConflict;
    }

    private void insertSocial(Long memberId, OAuthUserInfo info) {
        LocalDateTime now = LocalDateTime.now();
        MemUserSocial social = new MemUserSocial();
        social.setTenantId(0L);
        social.setMemberId(memberId);
        social.setProvider(info.getProvider());
        social.setOpenId(info.getOpenId());
        social.setUnionId(info.getUnionId());
        social.setNickname(normalizeText(info.getNickname(), NICKNAME_MAX));
        social.setAvatar(normalizeAvatar(info.getAvatar()));
        social.setEmail(verifiedEmail(info));
        social.setMobile(verifiedMobile(info));
        social.setEmailVerified(verifiedEmail(info) == null ? VERIFIED_NO : VERIFIED_YES);
        social.setMobileVerified(verifiedMobile(info) == null ? VERIFIED_NO : VERIFIED_YES);
        social.setRawData(info.getRawData());
        social.setLastLoginTime(now);
        social.setCreateTime(now);
        socialMapper.insert(social);
    }

    private void touch(MemUserSocial social, OAuthUserInfo info) {
        socialMapper.update(null, new LambdaUpdateWrapper<MemUserSocial>()
                .eq(MemUserSocial::getSocialId, social.getSocialId())
                .set(MemUserSocial::getUnionId, info.getUnionId())
                .set(MemUserSocial::getNickname, normalizeText(info.getNickname(), NICKNAME_MAX))
                .set(MemUserSocial::getAvatar, normalizeAvatar(info.getAvatar()))
                .set(MemUserSocial::getEmail, verifiedEmail(info))
                .set(MemUserSocial::getMobile, verifiedMobile(info))
                .set(MemUserSocial::getEmailVerified, verifiedEmail(info) == null ? VERIFIED_NO : VERIFIED_YES)
                .set(MemUserSocial::getMobileVerified, verifiedMobile(info) == null ? VERIFIED_NO : VERIFIED_YES)
                .set(MemUserSocial::getRawData, info.getRawData())
                .set(MemUserSocial::getLastLoginTime, LocalDateTime.now())
                .set(MemUserSocial::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 头像落地：下载转存文件中心，成功存 fileId；失败退回原外链，
     * 门户用 {@code referrerpolicy=no-referrer} 兜底历史外链。
     */
    private String resolveAvatar(OAuthUserInfo info) {
        String url = normalizeAvatar(info.getAvatar());
        if (url == null) {
            return null;
        }
        String fileId = avatarLocalizer.localize(info.getProvider(), url);
        if (fileId != null) {
            return fileId;
        }
        return url;
    }

    /**
     * 生成站内用户名。
     *
     * <p>优先用“平台前缀 + openId 尾段”，人一眼能看出账号来源；冲突时加随机后缀。
     * {@code attempt>0} 表示上一次插入撞了唯一键，进一步加随机段。</p>
     */
    private String generateUsername(OAuthUserInfo info, int attempt) {
        String prefix = info.getProvider() == null ? "oauth"
                : info.getProvider().replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (prefix.isEmpty()) {
            prefix = "oauth";
        }
        if (prefix.length() > 12) {
            prefix = prefix.substring(0, 12);
        }
        String openId = info.getOpenId() == null ? "" : info.getOpenId().replaceAll("[^a-zA-Z0-9]", "");
        String tail = openId.length() > 6 ? openId.substring(openId.length() - 6) : openId;
        if (tail.isEmpty()) {
            tail = String.format("%06d", RANDOM.nextInt(1_000_000));
        }
        String base = truncate(prefix + "_" + tail, USERNAME_MAX - 5);
        String candidate = attempt == 0 && !usernameExists(base)
                ? base
                : truncate(base + "_" + String.format("%04d", RANDOM.nextInt(10_000)), USERNAME_MAX);
        for (int i = 0; i < 20; i++) {
            if (!usernameExists(candidate)) {
                return candidate;
            }
            candidate = truncate(base + "_" + String.format("%04d", RANDOM.nextInt(10_000)), USERNAME_MAX);
        }
        return truncate(prefix + "_" + UUID.randomUUID().toString().replace("-", ""), USERNAME_MAX);
    }

    private boolean usernameExists(String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<MemUser>()
                .eq(MemUser::getUsername, username));
        return count != null && count > 0;
    }

    private String normalizeNickname(OAuthUserInfo info) {
        String nickname = normalizeText(info.getNickname(), NICKNAME_MAX);
        if (StringUtils.hasText(nickname)) {
            return nickname;
        }
        return truncate(info.getProvider() == null ? "会员" : info.getProvider() + "用户", NICKNAME_MAX);
    }

    /** 只接受 http(s) 头像或数字 fileId；第三方返回的非法值直接丢弃。 */
    private String normalizeAvatar(String avatar) {
        if (!StringUtils.hasText(avatar)) {
            return null;
        }
        String value = avatar.trim().replace("&amp;", "&");
        if (value.length() > AVATAR_MAX) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || value.matches("\\d{1,19}")) {
            return value;
        }
        return null;
    }

    private String verifiedEmail(OAuthUserInfo info) {
        if (!info.isEmailVerified() || !StringUtils.hasText(info.getEmail())) {
            return null;
        }
        String value = info.getEmail().trim().toLowerCase(Locale.ROOT);
        if (value.length() > EMAIL_MAX || value.indexOf('@') <= 0) {
            return null;
        }
        return value;
    }

    private String verifiedMobile(OAuthUserInfo info) {
        if (!info.isMobileVerified() || !StringUtils.hasText(info.getMobile())) {
            return null;
        }
        String value = info.getMobile().trim().replace(" ", "");
        if (value.length() > MOBILE_MAX || !value.matches("^\\+?\\d{5,20}$")) {
            return null;
        }
        return value;
    }

    private MemUser first(List<MemUser> users) {
        return users == null || users.isEmpty() ? null : users.get(0);
    }

    private String normalizeText(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return truncate(value.trim(), max);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
