package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 门户可见的第三方登录入口。
 *
 * <p>只下发展示字段，不含 ClientId / Secret / 端点等后台配置。</p>
 */
@Data
public class MemberOAuthProviderItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;

    private String displayName;

    /** cn：国内；global：国外 */
    private String group;
}
