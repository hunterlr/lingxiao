package com.lingxiao.modules.member.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberCaptchaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 会员验证码。
 *
 * <p>独立于后台验证码：使用 Sa-Token 的 {@link SaTokenDao} 存储，单机自动落内存、
 * {@code lx.redis.enabled=true} 时自动落 Redis，不需要会员模块依赖系统模块的
 * Caffeine / Redis 实现。</p>
 *
 * <p>两级开关：</p>
 * <ol>
 *   <li>{@code lx.captcha.enabled} 总闸，默认 true，自动化测试可关闭；</li>
 *   <li>{@code mem.security.captchaEnabled} 会员域开关，参数页可改，运行期生效。</li>
 * </ol>
 *
 * <p>场景：{@code login} / {@code register} / {@code reset}。场景参与 Key，
 * 登录验证码不能拿去注册，注册验证码不能拿去重置密码。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberCaptchaService {

    private static final long EXPIRE_SECONDS = 300;
    private static final String KEY_PREFIX = "lx:member:captcha:";
    private static final String CONFIG_KEY = "mem.security.captchaEnabled";
    private static final Set<String> SCENES = new HashSet<>(Arrays.asList("login", "register", "reset"));

    @Value("${lx.captcha.enabled:true}")
    private boolean masterEnabled;

    private final ConfigProvider configProvider;

    public boolean isEnabled() {
        if (!masterEnabled) {
            return false;
        }
        try {
            return configProvider.getBooleanValue(CONFIG_KEY, true);
        } catch (RuntimeException e) {
            // fail closed：参数库异常时宁可用户多输一次，也不能把登录接口裸露
            log.warn("读取会员验证码开关失败，按开启处理: {}", e.getMessage());
            return true;
        }
    }

    public MemberCaptchaResponse create(String scene) {
        if (!isEnabled()) {
            return MemberCaptchaResponse.disabled();
        }
        String normalized = normalizeScene(scene);
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 80);
        String key = UUID.randomUUID().toString().replace("-", "");
        SaManager.getSaTokenDao().set(KEY_PREFIX + normalized + ":" + key,
                captcha.getCode(), EXPIRE_SECONDS);
        MemberCaptchaResponse response = new MemberCaptchaResponse();
        response.setEnabled(true);
        response.setKey(key);
        response.setImage("data:image/png;base64," + captcha.getImageBase64());
        response.setExpireIn(EXPIRE_SECONDS);
        return response;
    }

    public void verify(String scene, String key, String code) {
        if (!isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(key) || !StringUtils.hasText(code)) {
            throw new ServiceException(ErrorCode.CAPTCHA_INVALID);
        }
        String storeKey = KEY_PREFIX + normalizeScene(scene) + ":" + key.trim();
        SaTokenDao dao = SaManager.getSaTokenDao();
        String expected;
        try {
            expected = dao.get(storeKey);
        } catch (RuntimeException e) {
            log.warn("读取会员验证码失败，按验证码错误处理: {}", e.getMessage());
            throw new ServiceException(ErrorCode.CAPTCHA_INVALID);
        }
        // 无论成功失败都删除，避免同一验证码被暴力尝试
        dao.delete(storeKey);
        if (expected == null || !expected.equalsIgnoreCase(code.trim())) {
            throw new ServiceException(ErrorCode.CAPTCHA_INVALID);
        }
    }

    private String normalizeScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return "login";
        }
        String normalized = scene.trim().toLowerCase();
        if (!SCENES.contains(normalized)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.captcha.scene");
        }
        return normalized;
    }
}
