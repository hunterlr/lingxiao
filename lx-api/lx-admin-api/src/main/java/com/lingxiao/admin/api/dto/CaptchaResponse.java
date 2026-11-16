package com.lingxiao.admin.api.dto;

import lombok.Data;

/**
 * 验证码响应。
 *
 * <p>{@code image} 是 data URI（{@code data:image/png;base64,...}），
 * 前端直接放进 img 的 src，不需要再拼前缀。</p>
 *
 * <p>{@code enabled} 是给登录页用的：平台可能通过系统参数
 * {@code sys.account.captchaEnabled} 关闭验证码，此时 {@code image} 为空，
 * 前端应隐藏验证码输入框，而不是显示一张空白图。</p>
 */
@Data
public class CaptchaResponse {

    /** 是否开启验证码；false 时其余字段为空 */
    private boolean enabled;

    /** 验证码标识，登录时原样带回 */
    private String captchaKey;

    /** data URI 图片 */
    private String image;

    /** 有效期（秒） */
    private long expiresIn;

    public CaptchaResponse(boolean enabled, String captchaKey, String image, long expiresIn) {
        this.enabled = enabled;
        this.captchaKey = captchaKey;
        this.image = image;
        this.expiresIn = expiresIn;
    }

    /** 关闭验证码时的占位响应。 */
    public static CaptchaResponse disabled() {
        return new CaptchaResponse(false, null, null, 0);
    }
}
