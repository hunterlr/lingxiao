package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 会员已绑定的第三方账号；不返回 openId 等平台标识。 */
@Data
public class MemberOAuthBindingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;

    private String displayName;

    private String nickname;

    private String avatar;

    private LocalDateTime lastLoginTime;
}
