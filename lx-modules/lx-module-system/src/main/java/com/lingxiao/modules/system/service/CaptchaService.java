package com.lingxiao.modules.system.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.lingxiao.admin.api.dto.CaptchaResponse;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.system.service.captcha.CaptchaStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 图形验证码。
 *
 * <p>验证码的“存”由 {@link CaptchaStore} 决定：单机默认 Caffeine，
 * {@code lx.redis.enabled=true} 时自动换成 Redis（见 {@code RedisCaptchaStore}）。
 * 本类只负责生成、校验与开关判断，不关心底层是内存还是 Redis。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>验证码一次性使用，校验后立即删除；</li>
 *   <li>5 分钟过期；</li>
 *   <li>校验忽略大小写，避免用户因大小写反复失败。</li>
 * </ul>
 *
 * <p><b>两级开关：</b></p>
 * <ol>
 *   <li>{@code lx.captcha.enabled}：启动参数，默认 true，只给本地联调 / 自动化测试用。
 *       它是<b>总闸</b>，为 false 时无论参数设置怎么配都关闭；</li>
 *   <li>{@code sys.account.captchaEnabled}：系统参数（参数设置页可改），运行期生效。
 *       默认 true；改成 false 后登录页会隐藏验证码输入框。</li>
 * </ol>
 *
 * <p>为什么还要总闸：自动化测试识别不了图形码，而参数在 DB 里，
 * 测试环境需要在“连不上业务库参数”之前就能关闭验证码；同时生产可以靠启动参数强制锁定。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final long EXPIRE_SECONDS = 300;

    /** 参数设置里的键；内置参数（config_type=Y），只允许改值 */
    private static final String CONFIG_KEY = "sys.account.captchaEnabled";

    /**
     * 总闸，默认开启；生产禁止用启动参数关闭。
     * 关闭时参数设置里的开关也不再生效。
     */
    @Value("${lx.captcha.enabled:true}")
    private boolean masterEnabled;

    private final SysConfigService configService;
    private final CaptchaStore captchaStore;

    /** 当前验证码存储实现（Caffeine / Redis），用于启动日志与排查。 */
    public String storeMode() {
        return captchaStore.mode();
    }

    /**
     * 当前是否真的需要验证码：总闸 AND 参数设置。
     *
     * <p>读参数失败时<b>按开启处理</b>（fail closed）：参数库异常时宁可让用户多输一次，
     * 也不能因为读不到开关就把登录接口裸露出来。</p>
     */
    public boolean isEnabled() {
        if (!masterEnabled) {
            return false;
        }
        try {
            String value = configService.getValueByKey(CONFIG_KEY);
            return value == null || !"false".equalsIgnoreCase(value.trim());
        } catch (RuntimeException e) {
            log.warn("读取验证码开关失败，按开启处理: {}", e.getMessage());
            return true;
        }
    }

    public CaptchaResponse create() {
        if (!isEnabled()) {
            return CaptchaResponse.disabled();
        }
        // 宽 120、高 40、4 位字符、干扰线 80
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 80);
        String key = UUID.randomUUID().toString().replace("-", "");
        captchaStore.put(key, captcha.getCode(), EXPIRE_SECONDS);
        String image = "data:image/png;base64," + captcha.getImageBase64();
        return new CaptchaResponse(true, key, image, EXPIRE_SECONDS);
    }

    /**
     * 校验并消费验证码。
     *
     * <p>关闭状态下直接放行；开启状态下 key / code 缺失也按“验证码错误”处理，
     * 所以 {@code LoginRequest} 不再用 {@code @NotBlank} 强制它们必填。</p>
     *
     * @throws ServiceException 验证码不存在、过期或不匹配
     */
    public void verify(String key, String code) {
        if (!isEnabled()) {
            return;
        }
        if (key == null || code == null) {
            throw new ServiceException(ErrorCode.CAPTCHA_INVALID);
        }
        String expected = captchaStore.get(key);
        // 无论成功失败都删除，避免同一验证码被暴力尝试
        captchaStore.remove(key);
        if (expected == null || !expected.equalsIgnoreCase(code.trim())) {
            throw new ServiceException(ErrorCode.CAPTCHA_INVALID);
        }
    }
}
