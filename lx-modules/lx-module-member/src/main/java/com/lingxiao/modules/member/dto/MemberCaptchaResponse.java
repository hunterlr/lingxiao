package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 会员验证码响应。
 *
 * <p>与后台的 {@code CaptchaResponse} 字段保持一致，但不复用：
 * 会员模块不依赖 lx-admin-api，两个域可以各自演进。</p>
 */
@Data
public class MemberCaptchaResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled;

    private String key;

    private String image;

    private long expireIn;

    public static MemberCaptchaResponse disabled() {
        MemberCaptchaResponse response = new MemberCaptchaResponse();
        response.setEnabled(false);
        response.setExpireIn(0);
        return response;
    }
}
