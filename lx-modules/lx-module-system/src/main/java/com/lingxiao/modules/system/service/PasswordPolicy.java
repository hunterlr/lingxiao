package com.lingxiao.modules.system.service;

import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 密码复杂度策略。
 *
 * <p>策略由参数 {@code sys.account.passwordPolicy} 控制，取值：</p>
 * <ul>
 *   <li>{@code off}：不校验复杂度（长度仍受各 DTO 的 6~64 约束）；</li>
 *   <li>{@code basic}：至少 8 位，且同时包含英文字母与数字（默认）；</li>
 *   <li>{@code strong}：至少 10 位，且同时包含大写、小写、数字、符号。</li>
 * </ul>
 *
 * <p><b>只在“设置新密码”时校验</b>：新增用户、管理员重置密码、个人中心改密。
 * 登录不校验复杂度，否则策略一改，所有老密码用户都会被挡在门外。</p>
 *
 * <p>参数值非法（手工改库、导入旧数据）时回退 {@code basic} 并记 warn，
 * 不因为一个参数写错就让所有改密请求 500。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    /** 参数 key：与 V14 迁移落库的 sys_config 保持一致 */
    public static final String CONFIG_KEY = "sys.account.passwordPolicy";

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    /** 符号：既不是英文字母也不是数字的可见字符 */
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");

    private final ConfigProvider configProvider;

    /**
     * 校验新密码；不符合策略时抛业务异常，前端直接展示 msg。
     */
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
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "密码至少 10 位，且必须同时包含大写字母、小写字母、数字和符号");
            }
            return;
        }
        if (password.length() < 8
                || !LETTER.matcher(password).find()
                || !DIGIT.matcher(password).find()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "密码至少 8 位，且必须同时包含英文字母和数字");
        }
    }

    /**
     * 当前生效的策略：始终返回 {@code off / basic / strong} 之一。
     */
    public String resolvePolicy() {
        String value = configProvider.getValue(CONFIG_KEY);
        if (value == null || value.trim().isEmpty()) {
            return "basic";
        }
        String policy = value.trim().toLowerCase(Locale.ROOT);
        if ("off".equals(policy) || "basic".equals(policy) || "strong".equals(policy)) {
            return policy;
        }
        log.warn("密码策略参数值非法，按 basic 处理: {}", value);
        return "basic";
    }
}
