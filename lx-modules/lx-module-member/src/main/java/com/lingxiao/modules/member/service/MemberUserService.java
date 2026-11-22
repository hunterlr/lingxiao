package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.common.web.util.ServletUtils;
import com.lingxiao.modules.member.dto.MemberCreateRequest;
import com.lingxiao.modules.member.dto.MemberForgotPasswordRequest;
import com.lingxiao.modules.member.dto.MemberInfoVO;
import com.lingxiao.modules.member.dto.MemberLoginRequest;
import com.lingxiao.modules.member.dto.MemberLoginResponse;
import com.lingxiao.modules.member.dto.MemberPasswordChangeRequest;
import com.lingxiao.modules.member.dto.MemberPasswordSetRequest;
import com.lingxiao.modules.member.dto.MemberProfileUpdateRequest;
import com.lingxiao.modules.member.dto.MemberRegisterRequest;
import com.lingxiao.modules.member.dto.MemberResetPasswordRequest;
import com.lingxiao.modules.member.dto.MemberUpdateRequest;
import com.lingxiao.modules.member.entity.MemLevel;
import com.lingxiao.modules.member.entity.MemPasswordReset;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemPasswordResetMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import com.lingxiao.modules.member.security.MemberLoginHelper;
import com.lingxiao.modules.member.security.MemberPasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 会员账号服务。
 *
 * <p>覆盖会员端（注册 / 登录 / 个人中心 / 找回密码）与后台会员管理
 * （列表 / 新增 / 编辑 / 启停 / 重置密码 / 删除）。一个类把“同一张 mem_user
 * 的读写”收在一起，避免会员端和后台各写一份校验逻辑。</p>
 *
 * <p><b>与平台账号的关系：</b>完全不共享。这里不会读写 {@code sys_user}，
 * 也不会调用平台 Token；会员密码哈希用本模块的 BCrypt 实例，后台账号密码
 * 换算法或换策略都不影响会员。</p>
 *
 * <p><b>登录失败计数：</b>落在 {@code mem_user.login_fail_count / lock_time}，
 * 与后台登录策略分开配置（{@code mem.account.*}）。锁定期间直接拒绝，
 * 不再比对密码，避免锁定期内继续撞库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberUserService {

    private static final int DEFAULT_MAX_RETRY = 5;
    private static final int DEFAULT_LOCK_MINUTES = 10;
    private static final int DEFAULT_RESET_EXPIRE_MINUTES = 10;
    private static final int DEFAULT_REGISTER_LIMIT = 5;
    private static final int DEFAULT_LOGIN_LIMIT = 30;
    private static final int DEFAULT_RESET_LIMIT = 5;
    private static final int DEFAULT_RATE_LIMIT_MINUTES = 10;
    private static final Set<String> SEX_VALUES = new HashSet<>(Arrays.asList("0", "1", "2"));
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 密码标记：第三方自动建号写随机占位密码，用户并不知道它 */
    private static final String PASSWORD_SET_YES = "1";
    private static final String PASSWORD_SET_NO = "0";

    /** 登录标识形态：只允许纯数字 / 可带一个前导 +，长度 5~20 */
    private static final Pattern MOBILE_ACCOUNT = Pattern.compile("^\\+?\\d{5,20}$");

    private final MemUserMapper userMapper;
    private final MemPasswordResetMapper passwordResetMapper;
    private final MemberLevelService levelService;
    private final MemberPasswordPolicy passwordPolicy;
    private final MemberPasswordEncoder passwordEncoder;
    private final MemberCaptchaService captchaService;
    private final MemberTokenIssuer tokenIssuer;
    private final MemberResetCodeSender resetCodeSender;
    private final MemberRateLimitService rateLimitService;
    private final ConfigProvider configProvider;

    // ==================== 会员端 ====================

    /**
     * 注册并自动登录。
     *
     * <p>顺序：验证码 → 协议 → 账号格式 → 唯一性 → 密码策略 → 落库 → 发 Token。
     * 唯一性失败优先提示具体字段，不然用户只会看到“注册失败”。</p>
     */
    public MemberLoginResponse register(MemberRegisterRequest request) {
        limitRegister(request.getUsername());
        captchaService.verify("register", request.getCaptchaKey(), request.getCaptchaCode());
        if (!Boolean.TRUE.equals(request.getAgreement())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.agreement");
        }

        String username = requireText(request.getUsername(), "valid.username.required");
        validateUsername(username);
        String mobile = normalizeMobile(request.getMobile());
        String email = normalizeEmail(request.getEmail());
        if (mobileRequired() && mobile == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.mobileRequired");
        }
        if (emailRequired() && email == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.emailRequired");
        }
        ensureUnique(username, mobile, email, null);
        passwordPolicy.validate(request.getPassword());

        MemLevel level = levelService.defaultLevel();
        LocalDateTime now = LocalDateTime.now();
        MemUser user = new MemUser();
        user.setTenantId(0L);
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordSet(PASSWORD_SET_YES);
        user.setMobile(mobile);
        user.setEmail(email);
        user.setSex("2");
        user.setLevelId(level.getLevelId());
        user.setPoints(0);
        user.setGrowth(0);
        user.setStatus(CommonConstants.STATUS_NORMAL);
        user.setRegisterChannel("web");
        user.setRegisterIp(ServletUtils.getClientIp());
        user.setLoginFailCount(0);
        user.setLastActiveTime(now);
        insertUser(user);
        log.info("会员注册成功: memberId={}, username={}", user.getMemberId(), username);
        return tokenIssuer.issue(user, level.getLevelName());
    }

    /**
     * 登录：验证码 → 锁定判断 → 查账号 → 校验密码 → 状态 → 记录 → 发 Token。
     *
     * <p>{@code account} 按输入形态只查一个字段：含 {@code @} 视为邮箱，
     * 纯数字 / 带前导 {@code +} 视为手机号，其它视为用户名。这样即使一个值
     * 同时像两种标识，也不会出现“用户名命中 A、邮箱命中 B”时选错人的情况。</p>
     */
    public MemberLoginResponse login(MemberLoginRequest request) {
        limitLogin(request.getAccount());
        captchaService.verify("login", request.getCaptchaKey(), request.getCaptchaCode());

        MemUser user = findByAccount(request.getAccount());
        LocalDateTime now = LocalDateTime.now();

        if (user != null && user.getLockTime() != null && user.getLockTime().isAfter(now)) {
            long remaining = Math.max(1, Duration.between(now, user.getLockTime()).toMinutes() + 1);
            throw new ServiceException(ErrorCode.ACCOUNT_LOCKED, "error.2004.locked", remaining);
        }

        // 第三方自动建号的密码是随机占位值，用户并不知道；先给出明确指引，
        // 不把它当成“密码错误”去累计失败次数，避免把账号锁死。
        if (user != null && PASSWORD_SET_NO.equals(user.getPasswordSet())) {
            throw ServiceException.keyed(ErrorCode.PASSWORD_NOT_SET, "error.2006.passwordNotSet");
        }

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            if (user != null && registerFailure(user, now)) {
                int lockMinutes = lockMinutes();
                log.warn("会员因连续登录失败被锁定: memberId={}, username={}", user.getMemberId(), user.getUsername());
                throw new ServiceException(ErrorCode.ACCOUNT_LOCKED, "error.2004.failures", lockMinutes);
            }
            throw new ServiceException(ErrorCode.LOGIN_FAILED);
        }
        if (!CommonConstants.STATUS_NORMAL.equals(user.getStatus())) {
            throw new ServiceException(ErrorCode.ACCOUNT_DISABLED);
        }

        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, user.getMemberId())
                .set(MemUser::getLoginIp, ServletUtils.getClientIp())
                .set(MemUser::getLoginTime, now)
                .set(MemUser::getLastActiveTime, now)
                .set(MemUser::getLoginFailCount, 0)
                .set(MemUser::getLockTime, null));

        String levelName = levelService.nameOf(user.getLevelId());
        log.info("会员登录成功: memberId={}, username={}", user.getMemberId(), user.getUsername());
        return tokenIssuer.issue(user, levelName);
    }

    public void logout() {
        tokenIssuer.logoutCurrent();
    }

    /** 个人中心：当前会员资料。 */
    public MemberInfoVO getInfo() {
        MemUser user = currentUser();
        MemberInfoVO vo = new MemberInfoVO();
        vo.setMemberId(user.getMemberId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPasswordSet(user.getPasswordSet());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setSex(user.getSex());
        vo.setLevelId(user.getLevelId());
        vo.setLevelName(levelService.nameOf(user.getLevelId()));
        vo.setPoints(user.getPoints());
        vo.setGrowth(user.getGrowth());
        vo.setStatus(user.getStatus());
        vo.setRegisterTime(user.getCreateTime());
        vo.setLoginTime(user.getLoginTime());
        return vo;
    }

    /**
     * 个人中心：修改资料。
     *
     * <p>只允许改昵称 / 头像 / 性别；手机号、邮箱涉及换绑验证，不在本接口改。
     * 昵称写回 Session，会员端顶部信息不用重新登录就能刷新。</p>
     */
    public MemberInfoVO updateProfile(MemberProfileUpdateRequest request) {
        MemUser user = currentUser();
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (nickname.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.nickname");
        }
        String sex = normalizeSex(request.getSex(), user.getSex());

        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, user.getMemberId())
                .set(MemUser::getNickname, nickname)
                .set(MemUser::getAvatar, trimToNull(request.getAvatar()))
                .set(MemUser::getSex, sex)
                .set(MemUser::getLastActiveTime, LocalDateTime.now()));
        user.setNickname(nickname);
        MemberLoginHelper.refreshProfile(nickname, user.getLevelId());
        log.info("会员修改个人资料: memberId={}", user.getMemberId());
        return getInfo();
    }

    /**
     * 个人中心：修改密码。
     *
     * <p>先校验原密码，再落新密码，最后踢掉该会员全部会话；旧 Token 立即失效。</p>
     */
    public void changePassword(MemberPasswordChangeRequest request) {
        MemUser user = currentUser();
        if (PASSWORD_SET_NO.equals(user.getPasswordSet())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.passwordSetRequired");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new ServiceException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.samePassword");
        }
        passwordPolicy.validate(request.getNewPassword());

        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, user.getMemberId())
                .set(MemUser::getPassword, passwordEncoder.encode(request.getNewPassword())));
        tokenIssuer.logoutAll(user.getMemberId());
        log.info("会员自助修改密码: memberId={}", user.getMemberId());
    }

    /**
     * 第三方建号首次设置密码。
     *
     * <p>只允许 {@code password_set='0'} 的账号调用；已设置密码的账号必须走
     * “修改密码”并校验原密码。设置成功后保留当前会话，用户不用重新登录。</p>
     */
    public void setPassword(MemberPasswordSetRequest request) {
        MemUser user = currentUser();
        if (!PASSWORD_SET_NO.equals(user.getPasswordSet())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.passwordAlreadySet");
        }
        passwordPolicy.validate(request.getNewPassword());
        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, user.getMemberId())
                .set(MemUser::getPassword, passwordEncoder.encode(request.getNewPassword()))
                .set(MemUser::getPasswordSet, PASSWORD_SET_YES)
                .set(MemUser::getLoginFailCount, 0)
                .set(MemUser::getLockTime, null)
                .set(MemUser::getLastActiveTime, LocalDateTime.now()));
        log.info("会员首次设置登录密码: memberId={}", user.getMemberId());
    }

    /**
     * 找回密码第一步：申请验证码。
     *
     * <p>顺序：限流 → 图形验证码 → 查账号 → 发码。无论账号是否存在都返回成功，
     * 避免被用来枚举会员账号。验证码只存 BCrypt 哈希，有效期由
     * {@code mem.register.resetCodeExpireMinutes} 控制。</p>
     */
    public void forgotPassword(MemberForgotPasswordRequest request) {
        String account = request.getAccount();
        limitReset("forgot", account);
        captchaService.verify("reset", request.getCaptchaKey(), request.getCaptchaCode());
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String hash = passwordEncoder.encode(code);
        int expireMinutes = resetExpireMinutes();

        MemUser user = findByAccount(account);
        if (user == null) {
            // 仍然做一次哈希，避免“账号不存在”明显更快被探测出来
            log.debug("会员找回密码：账号不存在，静默返回");
            return;
        }

        // 同一会员的历史未用验证码全部作废，避免旧码继续可用
        passwordResetMapper.update(null, new LambdaUpdateWrapper<MemPasswordReset>()
                .eq(MemPasswordReset::getMemberId, user.getMemberId())
                .eq(MemPasswordReset::getUsed, "0")
                .set(MemPasswordReset::getUsed, "1"));

        LocalDateTime now = LocalDateTime.now();
        MemPasswordReset reset = new MemPasswordReset();
        reset.setMemberId(user.getMemberId());
        reset.setAccount(account);
        reset.setCodeHash(hash);
        reset.setExpireTime(now.plusMinutes(expireMinutes));
        reset.setUsed("0");
        reset.setCreateTime(now);
        passwordResetMapper.insert(reset);

        resetCodeSender.send(user, account, code, expireMinutes);
        log.info("会员申请找回密码: memberId={}, expireMinutes={}", user.getMemberId(), expireMinutes);
    }

    /** 找回密码第二步：校验验证码并重置密码。 */
    public void resetPassword(MemberResetPasswordRequest request) {
        limitReset("reset", request.getAccount());
        MemUser user = findByAccount(request.getAccount());
        if (user == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.resetCodeInvalid");
        }

        List<MemPasswordReset> records = passwordResetMapper.selectList(
                new LambdaQueryWrapper<MemPasswordReset>()
                        .eq(MemPasswordReset::getMemberId, user.getMemberId())
                        .eq(MemPasswordReset::getUsed, "0")
                        .orderByDesc(MemPasswordReset::getCreateTime)
                        .orderByDesc(MemPasswordReset::getResetId));
        MemPasswordReset reset = records.isEmpty() ? null : records.get(0);
        if (reset == null || reset.getExpireTime() == null
                || reset.getExpireTime().isBefore(LocalDateTime.now())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.resetCodeExpired");
        }
        if (!passwordEncoder.matches(request.getCode(), reset.getCodeHash())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.resetCodeInvalid");
        }
        passwordPolicy.validate(request.getNewPassword());

        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, user.getMemberId())
                .set(MemUser::getPassword, passwordEncoder.encode(request.getNewPassword()))
                .set(MemUser::getPasswordSet, PASSWORD_SET_YES)
                .set(MemUser::getLoginFailCount, 0)
                .set(MemUser::getLockTime, null));
        passwordResetMapper.update(null, new LambdaUpdateWrapper<MemPasswordReset>()
                .eq(MemPasswordReset::getMemberId, user.getMemberId())
                .eq(MemPasswordReset::getUsed, "0")
                .set(MemPasswordReset::getUsed, "1")
                .set(MemPasswordReset::getUseTime, LocalDateTime.now()));
        tokenIssuer.logoutAll(user.getMemberId());
        log.info("会员通过验证码重置密码: memberId={}", user.getMemberId());
    }

    // ==================== 后台会员管理 ====================

    public PageResult<MemUser> page(long pageNum, long pageSize, String keyword,
                                    String status, Long levelId) {
        Page<MemUser> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<MemUser> wrapper = new LambdaQueryWrapper<MemUser>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(MemUser::getUsername, keyword)
                        .or().like(MemUser::getNickname, keyword)
                        .or().like(MemUser::getMobile, keyword)
                        .or().like(MemUser::getEmail, keyword))
                .eq(StringUtils.hasText(status), MemUser::getStatus, status)
                .eq(levelId != null, MemUser::getLevelId, levelId)
                .orderByDesc(MemUser::getCreateTime)
                .orderByDesc(MemUser::getMemberId);
        Page<MemUser> result = userMapper.selectPage(page, wrapper);

        Map<Long, String> levelNames = levelService.nameMap();
        result.getRecords().forEach(item -> item.setLevelName(levelNames.get(item.getLevelId())));
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public MemUser get(Long memberId) {
        MemUser user = mustGet(memberId);
        user.setLevelName(levelService.nameOf(user.getLevelId()));
        return user;
    }

    public Long create(MemberCreateRequest request) {
        String username = requireText(request.getUsername(), "valid.username.required");
        validateUsername(username);
        String mobile = normalizeMobile(request.getMobile());
        String email = normalizeEmail(request.getEmail());
        ensureUnique(username, mobile, email, null);
        passwordPolicy.validate(request.getPassword());
        MemLevel level = request.getLevelId() == null
                ? levelService.defaultLevel()
                : levelService.requireEnabled(request.getLevelId());

        LocalDateTime now = LocalDateTime.now();
        MemUser user = new MemUser();
        user.setTenantId(0L);
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordSet(PASSWORD_SET_YES);
        user.setMobile(mobile);
        user.setEmail(email);
        user.setAvatar(trimToNull(request.getAvatar()));
        user.setSex(normalizeSex(request.getSex(), "2"));
        user.setLevelId(level.getLevelId());
        user.setPoints(0);
        user.setGrowth(0);
        user.setStatus(normalizeStatus(request.getStatus(), CommonConstants.STATUS_NORMAL));
        user.setRegisterChannel("admin");
        user.setRegisterIp(ServletUtils.getClientIp());
        user.setLoginFailCount(0);
        user.setRemark(trimToNull(request.getRemark()));
        user.setCreateBy(operatorId());
        user.setLastActiveTime(now);
        insertUser(user);
        log.info("后台新增会员: memberId={}, username={}, operator={}",
                user.getMemberId(), username, operatorId());
        return user.getMemberId();
    }

    public void update(Long memberId, MemberUpdateRequest request) {
        MemUser existing = mustGet(memberId);
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (nickname.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.nickname");
        }
        String mobile = normalizeMobile(request.getMobile());
        String email = normalizeEmail(request.getEmail());
        ensureUnique(existing.getUsername(), mobile, email, memberId);
        MemLevel level = request.getLevelId() == null
                ? levelService.requireEnabled(existing.getLevelId())
                : levelService.requireEnabled(request.getLevelId());
        String status = normalizeStatus(request.getStatus(), existing.getStatus());

        try {
            userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                    .eq(MemUser::getMemberId, memberId)
                    .set(MemUser::getNickname, nickname)
                    .set(MemUser::getMobile, mobile)
                    .set(MemUser::getEmail, email)
                    .set(MemUser::getAvatar, trimToNull(request.getAvatar()))
                    .set(MemUser::getSex, normalizeSex(request.getSex(), existing.getSex()))
                    .set(MemUser::getLevelId, level.getLevelId())
                    .set(MemUser::getStatus, status)
                    .set(MemUser::getRemark, trimToNull(request.getRemark()))
                    .set(MemUser::getUpdateBy, operatorId()));
        } catch (DuplicateKeyException e) {
            throw duplicateKeyError(e);
        }

        // 停用后旧 Token 必须立即失效，否则会员在 Token 过期前还能继续访问
        if (!CommonConstants.STATUS_NORMAL.equals(status)) {
            tokenIssuer.logoutAll(memberId);
        }
        log.info("后台编辑会员: memberId={}, operator={}", memberId, operatorId());
    }

    /** 启用 / 停用；停用同时踢掉全部会话。 */
    public void changeStatus(Long memberId, String status) {
        MemUser existing = mustGet(memberId);
        String normalized = normalizeStatus(status, null);
        if (normalized == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.status");
        }
        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, memberId)
                .set(MemUser::getStatus, normalized)
                .set(MemUser::getUpdateBy, operatorId()));
        if (!CommonConstants.STATUS_NORMAL.equals(normalized)) {
            tokenIssuer.logoutAll(memberId);
        }
        log.info("后台修改会员状态: memberId={}, status={}, operator={}",
                memberId, normalized, operatorId());
    }

    /** 后台重置密码：不校验原密码，重置后踢掉全部会话。 */
    public void resetPassword(Long memberId, String newPassword) {
        MemUser existing = mustGet(memberId);
        passwordPolicy.validate(newPassword);
        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, memberId)
                .set(MemUser::getPassword, passwordEncoder.encode(newPassword))
                .set(MemUser::getPasswordSet, PASSWORD_SET_YES)
                .set(MemUser::getLoginFailCount, 0)
                .set(MemUser::getLockTime, null)
                .set(MemUser::getUpdateBy, operatorId()));
        tokenIssuer.logoutAll(memberId);
        log.info("后台重置会员密码: memberId={}, username={}, operator={}",
                memberId, existing.getUsername(), operatorId());
    }

    public void delete(Long memberId) {
        MemUser existing = mustGet(memberId);
        userMapper.deleteById(memberId);
        tokenIssuer.logoutAll(memberId);
        log.info("后台删除会员: memberId={}, username={}, operator={}",
                memberId, existing.getUsername(), operatorId());
    }

    /** 后台下拉：启用等级。 */
    public List<MemLevel> levelOptions() {
        return levelService.listEnabled();
    }

    // ==================== 内部方法 ====================

    /**
     * 当前平台操作人；非 Web 线程（定时任务 / 单测）拿不到上下文时返回 null，
     * 审计字段留空而不是让业务操作失败。
     */
    private Long operatorId() {
        try {
            return LoginHelper.getUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private MemUser currentUser() {
        Long memberId = MemberLoginHelper.getMemberId();
        if (memberId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        return mustGet(memberId);
    }

    private MemUser mustGet(Long memberId) {
        MemUser user = memberId == null ? null : userMapper.selectById(memberId);
        if (user == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member");
        }
        return user;
    }

    /**
     * 按输入形态只查一个字段：
     * <ul>
     *   <li>含 {@code @}：邮箱；</li>
     *   <li>纯数字 / 带一个前导 {@code +}：手机号；</li>
     *   <li>其它：用户名。</li>
     * </ul>
     *
     * <p>用户名在注册时已被限制为不能包含 {@code @}、也不能是纯数字，
     * 因此三种形态不会互相重叠，避免一个值同时命中两个账号。</p>
     */
    private MemUser findByAccount(String account) {
        String value = account == null ? "" : account.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.indexOf('@') >= 0) {
            return first(userMapper.selectList(new LambdaQueryWrapper<MemUser>()
                    .eq(MemUser::getEmail, value.toLowerCase(Locale.ROOT))));
        }
        if (MOBILE_ACCOUNT.matcher(value).matches()) {
            return first(userMapper.selectList(new LambdaQueryWrapper<MemUser>()
                    .eq(MemUser::getMobile, value)));
        }
        return first(userMapper.selectList(new LambdaQueryWrapper<MemUser>()
                .eq(MemUser::getUsername, value)));
    }

    private MemUser first(List<MemUser> users) {
        return users == null || users.isEmpty() ? null : users.get(0);
    }

    /**
     * 用户名 / 手机号 / 邮箱唯一性。
     *
     * <p>数据库 V33 已加生成列 + 唯一键，这里是写入前的友好提示；
     * 并发注册仍可能撞库，{@link #insertUser(MemUser)} 会把
     * {@link DuplicateKeyException} 翻成同样的字段错误。</p>
     */
    private void ensureUnique(String username, String mobile, String email, Long excludeId) {
        if (countBy(MemUser::getUsername, username, excludeId) > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.usernameExists");
        }
        if (mobile != null && countBy(MemUser::getMobile, mobile, excludeId) > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.mobileExists");
        }
        if (email != null && countBy(MemUser::getEmail, email, excludeId) > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.emailExists");
        }
    }

    private void insertUser(MemUser user) {
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw duplicateKeyError(e);
        }
    }

    /** 把数据库唯一键冲突翻成具体字段，避免前端只看到 5000 / 通用失败。 */
    private ServiceException duplicateKeyError(DuplicateKeyException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("uk_mem_user_mobile")) {
            return ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.mobileExists");
        }
        if (message.contains("uk_mem_user_email")) {
            return ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.emailExists");
        }
        return ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.usernameExists");
    }

    /**
     * 用户名不能包含 {@code @} 或全部为数字。
     *
     * <p>登录时按形态分流：含 {@code @} 只查邮箱、纯数字只查手机号。
     * 如果允许这类用户名注册，同名账号可能永远无法用用户名登录。</p>
     */
    private void validateUsername(String username) {
        if (username.indexOf('@') >= 0 || MOBILE_ACCOUNT.matcher(username).matches()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.usernameFormat");
        }
    }

    /** 邮箱统一小写去空格；数据库唯一键按 collation 不区分大小写，这里先归一化。 */
    private String normalizeEmail(String value) {
        String text = trimToNull(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private String normalizeMobile(String value) {
        return trimToNull(value);
    }

    private long countBy(com.baomidou.mybatisplus.core.toolkit.support.SFunction<MemUser, ?> column,
                         String value, Long excludeId) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<MemUser>()
                .eq(column, value)
                .ne(excludeId != null, MemUser::getMemberId, excludeId));
        return count == null ? 0L : count;
    }

    /**
     * 记录一次密码错误；达到阈值时锁定并清零计数。
     *
     * <p>锁定到期后再失败从 0 开始计数，避免上次锁定前的失败次数被继承。</p>
     *
     * @return {@code true} 表示本次失败触发了锁定
     */
    private boolean registerFailure(MemUser user, LocalDateTime now) {
        int failCount = user.getLoginFailCount() == null ? 0 : user.getLoginFailCount();
        if (user.getLockTime() != null && !user.getLockTime().isAfter(now)) {
            failCount = 0;
        }
        int next = failCount + 1;
        if (next >= maxRetryCount()) {
            userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                    .eq(MemUser::getMemberId, user.getMemberId())
                    .set(MemUser::getLoginFailCount, 0)
                    .set(MemUser::getLockTime, now.plusMinutes(lockMinutes())));
            return true;
        }
        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, user.getMemberId())
                .set(MemUser::getLoginFailCount, next)
                .set(MemUser::getLockTime, null));
        return false;
    }

    private int maxRetryCount() {
        int value = configProvider.getIntValue("mem.account.maxRetryCount", DEFAULT_MAX_RETRY);
        return Math.min(100, Math.max(1, value));
    }

    private int lockMinutes() {
        int value = configProvider.getIntValue("mem.account.lockMinutes", DEFAULT_LOCK_MINUTES);
        return Math.min(1440, Math.max(1, value));
    }

    private int resetExpireMinutes() {
        int value = configProvider.getIntValue("mem.register.resetCodeExpireMinutes",
                DEFAULT_RESET_EXPIRE_MINUTES);
        return Math.min(60, Math.max(5, value));
    }

    /**
     * 注册限流：同一用户名与同一 IP 各一个窗口。
     *
     * <p>用户名维度防单账号重放，IP 维度防脚本批量注册；IP 阈值是账号阈值的
     * 10 倍，正常用户不会碰到，脚本刷接口会先被挡住。</p>
     */
    private void limitRegister(String username) {
        int count = registerLimitCount();
        int minutes = registerLimitMinutes();
        rateLimitService.check(rateKey("register", "id", username), count, minutes);
        rateLimitService.check(rateKey("register", "ip", ServletUtils.getClientIp()),
                ipLimit(count), minutes);
    }

    /** 登录限流：账号 / IP 两个维度；账号锁定逻辑仍然落库，两者互不替代。 */
    private void limitLogin(String account) {
        int count = loginLimitCount();
        int minutes = loginLimitMinutes();
        rateLimitService.check(rateKey("login", "id", account), count, minutes);
        rateLimitService.check(rateKey("login", "ip", ServletUtils.getClientIp()),
                ipLimit(count), minutes);
    }

    /** 找回密码与重置密码共用一组参数，但动作 Key 分开，互不占用额度。 */
    private void limitReset(String action, String account) {
        int count = resetLimitCount();
        int minutes = resetLimitMinutes();
        rateLimitService.check(rateKey(action, "id", account), count, minutes);
        rateLimitService.check(rateKey(action, "ip", ServletUtils.getClientIp()),
                ipLimit(count), minutes);
    }

    private String rateKey(String action, String dimension, String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "-";
        return "member:" + action + ":" + dimension + ":" + normalized;
    }

    private int ipLimit(int accountLimit) {
        return (int) Math.min(1000L, Math.max(10L, (long) accountLimit * 10L));
    }

    private int registerLimitCount() {
        return clamp(configProvider.getIntValue("mem.register.rateLimitCount", DEFAULT_REGISTER_LIMIT), 1, 100);
    }

    private int registerLimitMinutes() {
        return clamp(configProvider.getIntValue("mem.register.rateLimitMinutes", DEFAULT_RATE_LIMIT_MINUTES), 1, 1440);
    }

    private int loginLimitCount() {
        return clamp(configProvider.getIntValue("mem.account.loginRateLimitCount", DEFAULT_LOGIN_LIMIT), 1, 1000);
    }

    private int loginLimitMinutes() {
        return clamp(configProvider.getIntValue("mem.account.loginRateLimitMinutes", DEFAULT_RATE_LIMIT_MINUTES), 1, 1440);
    }

    private int resetLimitCount() {
        return clamp(configProvider.getIntValue("mem.register.resetRateLimitCount", DEFAULT_RESET_LIMIT), 1, 100);
    }

    private int resetLimitMinutes() {
        return clamp(configProvider.getIntValue("mem.register.resetRateLimitMinutes", DEFAULT_RATE_LIMIT_MINUTES), 1, 1440);
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private boolean mobileRequired() {
        return configProvider.getBooleanValue("mem.register.mobileRequired", false);
    }

    private boolean emailRequired() {
        return configProvider.getBooleanValue("mem.register.emailRequired", false);
    }

    private String requireText(String value, String messageKey) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, messageKey);
        }
        return text;
    }

    private String normalizeSex(String sex, String fallback) {
        if (!StringUtils.hasText(sex)) {
            return StringUtils.hasText(fallback) ? fallback : "2";
        }
        String value = sex.trim();
        if (!SEX_VALUES.contains(value)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.sex");
        }
        return value;
    }

    /** 只接受 0 正常 / 1 停用；空值沿用默认值，非法值返回 null 由调用方报错。 */
    private String normalizeStatus(String status, String fallback) {
        if (!StringUtils.hasText(status)) {
            return fallback;
        }
        String value = status.trim();
        if (CommonConstants.STATUS_NORMAL.equals(value) || CommonConstants.STATUS_DISABLED.equals(value)) {
            return value;
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
