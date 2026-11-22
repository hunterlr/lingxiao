package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberChannelTestResult;
import com.lingxiao.modules.member.dto.MemberChannelUpdateRequest;
import com.lingxiao.modules.member.entity.MemChannelConfig;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemChannelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;

/**
 * 会员消息通道配置与发送。
 *
 * <p>通道配置落在 {@code mem_channel_config}，由后台「会员管理 → 渠道配置」
 * 维护，运行期读取，不缓存、不重启。{@code mode=dev} 使用写日志的开发实现，
 * {@code mode=prod} 使用部署方提供的 {@link MemberSmsSender} /
 * {@link MemberEmailSender} 实现；后者缺失时拒绝切到 prod，避免找回密码静默失效。</p>
 *
 * <p>找回密码下发时，通道被停用只记告警、不抛异常，避免通过接口差异判断
 * 账号是否存在；后台「测试发送」则会明确报错，方便运营排查配置。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberChannelConfigService {

    public static final String TYPE_SMS = "sms";
    public static final String TYPE_EMAIL = "email";
    public static final String MODE_DEV = "dev";
    public static final String MODE_PROD = "prod";
    public static final String STATUS_ENABLED = "0";

    private static final int TEST_EXPIRE_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MOBILE_PATTERN = "^1\\d{10}$";
    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final MemChannelConfigMapper configMapper;
    private final List<MemberSmsSender> smsSenders;
    private final List<MemberEmailSender> emailSenders;

    public List<MemChannelConfig> list() {
        return configMapper.selectList(new LambdaQueryWrapper<MemChannelConfig>()
                .orderByAsc(MemChannelConfig::getConfigId));
    }

    public MemChannelConfig get(Long configId) {
        return mustGet(configId);
    }

    /**
     * 修改通道配置。
     *
     * <p>{@code accessSecret} 留空表示不修改；切到 prod 前先确认部署方已经提供
     * 真实实现，否则直接拒绝，避免保存后找回密码全部失败。</p>
     */
    public void update(Long configId, MemberChannelUpdateRequest request) {
        MemChannelConfig config = mustGet(configId);
        String mode = request.getMode().trim();
        if (MODE_PROD.equals(mode)) {
            if (TYPE_SMS.equals(config.getChannelType())) {
                requireProdSmsSender();
            } else {
                requireProdEmailSender();
            }
        }
        config.setMode(mode);
        config.setProvider(trimToNull(request.getProvider()));
        config.setEndpoint(trimToNull(request.getEndpoint()));
        config.setAccessKey(trimToNull(request.getAccessKey()));
        if (StringUtils.hasText(request.getAccessSecret())) {
            config.setAccessSecret(request.getAccessSecret().trim());
        }
        config.setSignName(trimToNull(request.getSignName()));
        config.setTemplateCode(trimToNull(request.getTemplateCode()));
        config.setSender(trimToNull(request.getSender()));
        config.setStatus(request.getStatus());
        config.setRemark(trimToNull(request.getRemark()));
        configMapper.updateById(config);
        log.info("会员消息通道已更新: channel={}, mode={}, status={}",
                config.getChannelType(), config.getMode(), config.getStatus());
    }

    /**
     * 找回密码验证码下发。
     *
     * @return 通道可用并完成下发返回 true；通道缺失或停用返回 false（只记告警）
     */
    public boolean send(MemUser user, String channelType, String target, String code, int expireMinutes) {
        MemChannelConfig config = findByType(channelType);
        if (config == null || !STATUS_ENABLED.equals(config.getStatus())) {
            log.warn("会员消息通道未启用，验证码未下发: channel={}, memberId={}",
                    channelType, user == null ? null : user.getMemberId());
            return false;
        }
        if (TYPE_SMS.equals(channelType)) {
            resolveSmsSender(config.getMode()).sendResetCode(user, target, code, expireMinutes);
        } else {
            resolveEmailSender(config.getMode()).sendResetCode(user, target, code, expireMinutes);
        }
        return true;
    }

    /**
     * 后台「测试发送」：生成临时验证码并立即按当前配置下发。
     */
    public MemberChannelTestResult test(Long configId, String target) {
        MemChannelConfig config = mustGet(configId);
        if (!STATUS_ENABLED.equals(config.getStatus())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.channelDisabled");
        }
        String normalized = trimToNull(target);
        if (normalized == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.channel.targetRequired");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        MemUser testUser = new MemUser();
        testUser.setMemberId(0L);
        testUser.setUsername("channel-test");

        if (TYPE_SMS.equals(config.getChannelType())) {
            if (!normalized.matches(MOBILE_PATTERN)) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.channel.mobile");
            }
            resolveSmsSender(config.getMode()).sendResetCode(testUser, normalized, code, TEST_EXPIRE_MINUTES);
        } else if (TYPE_EMAIL.equals(config.getChannelType())) {
            if (!normalized.matches(EMAIL_PATTERN)) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.channel.email");
            }
            resolveEmailSender(config.getMode()).sendResetCode(testUser, normalized, code, TEST_EXPIRE_MINUTES);
        } else {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.channel.type");
        }

        MemberChannelTestResult result = new MemberChannelTestResult();
        result.setChannelType(config.getChannelType());
        result.setMode(config.getMode());
        result.setProvider(config.getProvider());
        result.setTarget(normalized);
        // 开发模式把验证码回显给运营，方便确认日志通道内容；生产模式绝不回显
        result.setCode(MODE_DEV.equals(config.getMode()) ? code : null);
        result.setMessage(MODE_DEV.equals(config.getMode())
                ? "开发通道已写入应用日志" : "已提交生产通道");
        return result;
    }

    private MemChannelConfig findByType(String channelType) {
        return configMapper.selectOne(new LambdaQueryWrapper<MemChannelConfig>()
                .eq(MemChannelConfig::getChannelType, channelType));
    }

    private MemChannelConfig mustGet(Long configId) {
        MemChannelConfig config = configId == null ? null : configMapper.selectById(configId);
        if (config == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.channel");
        }
        return config;
    }

    private MemberSmsSender resolveSmsSender(String mode) {
        if (MODE_PROD.equals(mode)) {
            return requireProdSmsSender();
        }
        return smsSenders.stream()
                .filter(sender -> sender instanceof DevMemberSmsSender)
                .findFirst()
                .orElseThrow(() -> ServiceException.keyed(ErrorCode.INTERNAL_ERROR, "error.1001.member.channelDevMissing"));
    }

    private MemberEmailSender resolveEmailSender(String mode) {
        if (MODE_PROD.equals(mode)) {
            return requireProdEmailSender();
        }
        return emailSenders.stream()
                .filter(sender -> sender instanceof DevMemberEmailSender)
                .findFirst()
                .orElseThrow(() -> ServiceException.keyed(ErrorCode.INTERNAL_ERROR, "error.1001.member.channelDevMissing"));
    }

    private MemberSmsSender requireProdSmsSender() {
        return smsSenders.stream()
                .filter(sender -> !(sender instanceof DevMemberSmsSender))
                .findFirst()
                .orElseThrow(() -> ServiceException.keyed(ErrorCode.BAD_REQUEST,
                        "error.1001.member.channelProdUnavailable"));
    }

    private MemberEmailSender requireProdEmailSender() {
        return emailSenders.stream()
                .filter(sender -> !(sender instanceof DevMemberEmailSender))
                .findFirst()
                .orElseThrow(() -> ServiceException.keyed(ErrorCode.BAD_REQUEST,
                        "error.1001.member.channelProdUnavailable"));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
