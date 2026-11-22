package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.web.util.ServletUtils;
import com.lingxiao.modules.member.dto.MemberContactChangeRequest;
import com.lingxiao.modules.member.dto.MemberContactCodeRequest;
import com.lingxiao.modules.member.entity.MemContactChange;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemContactChangeMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
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
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 会员手机号 / 邮箱换绑。
 *
 * <p><b>流程：</b></p>
 * <ol>
 *   <li>已登录会员提交新手机号 / 邮箱，服务端校验格式与唯一性；</li>
 *   <li>已设置密码的账号必须校验当前密码；第三方建号未设置密码时跳过；</li>
 *   <li>验证码发送到“新联系方式”，只存 BCrypt 哈希；</li>
 *   <li>提交验证码后改 {@code mem_user.mobile / email}，旧值立即失效；</li>
 *   <li>验证码一次性使用，申请新码会作废旧码。</li>
 * </ol>
 *
 * <p><b>边界：</b>只写 {@code mem_user} 与 {@code mem_contact_change}，
 * 不碰后台 {@code sys_user}，也不改变会员 Token；换绑成功后当前会话继续有效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberContactService {

    public static final String TYPE_MOBILE = "mobile";
    public static final String TYPE_EMAIL = "email";

    private static final String USED_NO = "0";
    private static final String USED_YES = "1";
    private static final String PASSWORD_SET_YES = "1";

    /** 与注册 / 登录保持一致的手机号形态：可带一个前导 +，长度 5~20 */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^\\+?\\d{5,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int DEFAULT_EXPIRE_MINUTES = 10;
    private static final int DEFAULT_RATE_LIMIT_COUNT = 5;
    private static final int DEFAULT_RATE_LIMIT_MINUTES = 10;

    private final MemUserMapper userMapper;
    private final MemContactChangeMapper changeMapper;
    private final MemberPasswordEncoder passwordEncoder;
    private final MemberChannelConfigService channelConfigService;
    private final MemberRateLimitService rateLimitService;
    private final ConfigProvider configProvider;

    /**
     * 发送换绑验证码到新联系方式。
     *
     * <p>先做限流与当前密码校验，再检查唯一性并落验证码记录；
     * 通道未启用或发送失败时明确报错，避免用户等不到验证码却没有任何提示。</p>
     */
    public void sendCode(Long memberId, MemberContactCodeRequest request) {
        MemUser user = mustGet(memberId);
        String type = normalizeType(request.getType());
        String value = normalizeValue(type, request.getValue());
        limit("send", memberId);
        verifyPassword(user, request.getPassword());

        String current = comparableCurrentValue(user, type);
        if (current != null && current.equals(value)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.contactUnchanged");
        }
        ensureUnique(user, type, value);

        int expireMinutes = expireMinutes();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now();

        // 同一会员同类型的旧验证码立即作废，避免旧码继续可用
        changeMapper.update(null, new LambdaUpdateWrapper<MemContactChange>()
                .eq(MemContactChange::getMemberId, user.getMemberId())
                .eq(MemContactChange::getContactType, type)
                .eq(MemContactChange::getUsed, USED_NO)
                .set(MemContactChange::getUsed, USED_YES)
                .set(MemContactChange::getUseTime, now));

        MemContactChange change = new MemContactChange();
        change.setMemberId(user.getMemberId());
        change.setContactType(type);
        change.setOldValue(current);
        change.setNewValue(value);
        change.setCodeHash(passwordEncoder.encode(code));
        change.setExpireTime(now.plusMinutes(expireMinutes));
        change.setUsed(USED_NO);
        change.setRequestIp(ServletUtils.getClientIp());
        change.setCreateTime(now);
        changeMapper.insert(change);

        boolean sent;
        try {
            sent = channelConfigService.send(user, channelType(type), value, code, expireMinutes);
        } catch (RuntimeException e) {
            // 生产通道网络 / 配置异常：作废刚写入的码，并把异常交回上层；
            // 通道缺失类业务异常保留原始提示，便于运维定位。
            markUsed(change.getChangeId());
            throw e;
        }
        if (!sent) {
            // 通道不可用时立刻作废刚写入的验证码，避免留下用户永远收不到的有效码
            markUsed(change.getChangeId());
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST,
                    "error.1001.member.contactChannelUnavailable");
        }

        log.info("会员申请换绑联系方式: memberId={}, type={}, expireMinutes={}",
                user.getMemberId(), type, expireMinutes);
    }

    /**
     * 校验验证码并完成换绑。
     *
     * <p>提交的新值必须与发码时的目标值一致；唯一键冲突会翻成具体字段错误。</p>
     */
    @Transactional
    public void change(Long memberId, MemberContactChangeRequest request) {
        MemUser user = mustGet(memberId);
        String type = normalizeType(request.getType());
        String value = normalizeValue(type, request.getValue());
        limit("change", memberId);

        String current = comparableCurrentValue(user, type);
        if (current != null && current.equals(value)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.contactUnchanged");
        }
        ensureUnique(user, type, value);

        MemContactChange change = latestUnused(user.getMemberId(), type);
        LocalDateTime now = LocalDateTime.now();
        if (change == null || change.getExpireTime() == null || change.getExpireTime().isBefore(now)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.contactCodeExpired");
        }
        if (!value.equals(change.getNewValue())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.contactCodeMismatch");
        }
        if (!passwordEncoder.matches(request.getCode(), change.getCodeHash())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.contactCodeInvalid");
        }

        SFunction<MemUser, ?> column = TYPE_EMAIL.equals(type) ? MemUser::getEmail : MemUser::getMobile;
        try {
            userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                    .eq(MemUser::getMemberId, user.getMemberId())
                    .set(column, value)
                    .set(MemUser::getLastActiveTime, now));
        } catch (DuplicateKeyException e) {
            throw duplicateKeyError(type);
        }

        // 同一会员同类型所有未使用码一起作废，防止并发下另一条码继续可用
        changeMapper.update(null, new LambdaUpdateWrapper<MemContactChange>()
                .eq(MemContactChange::getMemberId, user.getMemberId())
                .eq(MemContactChange::getContactType, type)
                .eq(MemContactChange::getUsed, USED_NO)
                .set(MemContactChange::getUsed, USED_YES)
                .set(MemContactChange::getUseTime, now));

        log.info("会员换绑联系方式成功: memberId={}, type={}", user.getMemberId(), type);
    }

    // ==================== 内部方法 ====================

    private MemUser mustGet(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        MemUser user = userMapper.selectById(memberId);
        if (user == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member");
        }
        return user;
    }

    private String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!TYPE_MOBILE.equals(value) && !TYPE_EMAIL.equals(value)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.contact.type");
        }
        return value;
    }

    private String normalizeValue(String type, String raw) {
        String text = StringUtils.hasText(raw) ? raw.trim() : "";
        if (text.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.contact.value.required");
        }
        if (TYPE_MOBILE.equals(type)) {
            if (text.length() > 32 || !MOBILE_PATTERN.matcher(text).matches()) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.contact.mobile");
            }
            return text;
        }
        String email = text.toLowerCase(Locale.ROOT);
        if (email.length() > 128 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.contact.email");
        }
        return email;
    }

    private void verifyPassword(MemUser user, String rawPassword) {
        if (!PASSWORD_SET_YES.equals(user.getPasswordSet())) {
            return;
        }
        if (!StringUtils.hasText(rawPassword)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.contact.passwordRequired");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new ServiceException(ErrorCode.OLD_PASSWORD_ERROR);
        }
    }

    private String comparableCurrentValue(MemUser user, String type) {
        String value = TYPE_EMAIL.equals(type) ? user.getEmail() : user.getMobile();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return TYPE_EMAIL.equals(type) ? text.toLowerCase(Locale.ROOT) : text;
    }

    private void ensureUnique(MemUser user, String type, String value) {
        Long tenantId = user.getTenantId() == null ? 0L : user.getTenantId();
        SFunction<MemUser, ?> column = TYPE_EMAIL.equals(type) ? MemUser::getEmail : MemUser::getMobile;
        Long count = userMapper.selectCount(new LambdaQueryWrapper<MemUser>()
                .eq(MemUser::getTenantId, tenantId)
                .eq(column, value)
                .ne(MemUser::getMemberId, user.getMemberId()));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST,
                    TYPE_EMAIL.equals(type)
                            ? "error.1001.member.emailExists"
                            : "error.1001.member.mobileExists");
        }
    }

    private MemContactChange latestUnused(Long memberId, String type) {
        List<MemContactChange> records = changeMapper.selectList(new LambdaQueryWrapper<MemContactChange>()
                .eq(MemContactChange::getMemberId, memberId)
                .eq(MemContactChange::getContactType, type)
                .eq(MemContactChange::getUsed, USED_NO)
                .orderByDesc(MemContactChange::getCreateTime)
                .orderByDesc(MemContactChange::getChangeId));
        return records == null || records.isEmpty() ? null : records.get(0);
    }

    private String channelType(String type) {
        return TYPE_MOBILE.equals(type)
                ? MemberChannelConfigService.TYPE_SMS
                : MemberChannelConfigService.TYPE_EMAIL;
    }

    /** 通道失败时立刻作废刚写入的验证码，避免留下用户收不到的有效码。 */
    private void markUsed(Long changeId) {
        changeMapper.update(null, new LambdaUpdateWrapper<MemContactChange>()
                .eq(MemContactChange::getChangeId, changeId)
                .eq(MemContactChange::getUsed, USED_NO)
                .set(MemContactChange::getUsed, USED_YES)
                .set(MemContactChange::getUseTime, LocalDateTime.now()));
    }

    private ServiceException duplicateKeyError(String type) {
        return ServiceException.keyed(ErrorCode.BAD_REQUEST,
                TYPE_EMAIL.equals(type)
                        ? "error.1001.member.emailExists"
                        : "error.1001.member.mobileExists");
    }

    private void limit(String action, Long memberId) {
        int count = clamp(configProvider.getIntValue(
                "mem.account.contactRateLimitCount", DEFAULT_RATE_LIMIT_COUNT), 1, 100);
        int minutes = clamp(configProvider.getIntValue(
                "mem.account.contactRateLimitMinutes", DEFAULT_RATE_LIMIT_MINUTES), 1, 1440);
        rateLimitService.check("member:contact:" + action + ":id:" + memberId, count, minutes);
        rateLimitService.check("member:contact:" + action + ":ip:" + ServletUtils.getClientIp(),
                ipLimit(count), minutes);
    }

    private int expireMinutes() {
        int value = configProvider.getIntValue(
                "mem.account.contactCodeExpireMinutes", DEFAULT_EXPIRE_MINUTES);
        return clamp(value, 5, 60);
    }

    private int ipLimit(int accountLimit) {
        return (int) Math.min(1000L, Math.max(10L, (long) accountLimit * 10L));
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
