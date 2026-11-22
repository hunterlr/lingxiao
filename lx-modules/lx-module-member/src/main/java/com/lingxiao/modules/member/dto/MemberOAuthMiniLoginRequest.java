package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 小程序等无网页授权入口的平台，由客户端把 {@code wx.login()} 的 code 直接交给后端。
 *
 * <p>{@code mode=bind} 时必须携带会员 Token，后端只从会话取会员 ID。</p>
 */
@Data
public class MemberOAuthMiniLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.member.oauth.provider.required}")
    @Size(max = 32, message = "{valid.member.oauth.provider.max}")
    private String provider;

    @NotBlank(message = "{valid.member.oauth.code.required}")
    @Size(max = 512, message = "{valid.member.oauth.code.max}")
    private String code;

    /** login（默认）/ bind */
    private String mode;

    /** 绑定成功或换票后门户要跳回的站内路径 */
    private String redirect;
}
