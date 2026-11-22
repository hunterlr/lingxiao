package com.lingxiao.modules.member.service;

import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 会员密码复杂度策略。
 *
 * <p>参数 {@code mem.security.passwordPolicy}：{@code off / basic / strong}，
 * 默认 {@code basic}（至少 8 位且同时含字母和数字）。与后台
 * {@code sys.account.passwordPolicy} 分开：运营可以给后台更严的策略，
 * 而不强制所有会员都设强密码。</p>
 *
 * <p>只在“设置新密码”时校验：注册、找回密码、自助改密、后台重置。
 * 登录不校验，否则策略调整会把老密码用户挡在门外。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberPasswordPolicy {

    public static final String CONFIG_KEY = "mem.security.passwordPolicy";

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");

    private final ConfigProvider configProvider;

    public void validate(String rawPassword) {
        String policy = resolvePolicy();
        if ("off".equals(policy)) {
            return;
        }
        String password = rawPassword == null ? "" : rawPassword;
        if ("strong".equals(policy)) {
            if (password.length() < 10
                    || !LOWER.matcher(password).find()
                    || !UPPER.matcher(password).find()
                    || !DIGIT.matcher(password).find()
                    || !SYMBOL.matcher(password).find()) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.password.policy.strong");
            }
            return;
        }
        if (password.length() < 8
                || !LETTER.matcher(password).find()
                || !DIGIT.matcher(password).find()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.password.policy.basic");
        }
    }

    public String resolvePolicy() {
        String value = configProvider.getValue(CONFIG_KEY);
        if (value == null || value.trim().isEmpty()) {
            return "basic";
        }
        String policy = value.trim().toLowerCase(Locale.ROOT);
        if ("off".equals(policy) || "basic".equals(policy) || "strong".equals(policy)) {
            return policy;
        }
        log.warn("会员密码策略参数值非法，按 basic 处理: {}", value);
        return "basic";
    }
}
