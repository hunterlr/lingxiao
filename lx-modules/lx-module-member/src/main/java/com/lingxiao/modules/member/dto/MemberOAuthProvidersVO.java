package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 门户第三方登录入口列表。
 *
 * <p>总开关关闭时 {@code enabled=false} 且列表为空，登录页据此隐藏整块入口，
 * 不把“功能关闭”当成接口错误，避免整页白屏。</p>
 */
@Data
public class MemberOAuthProvidersVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled;

    /** 是否允许首次授权直接建号 */
    private boolean autoRegister;

    private List<MemberOAuthProviderItemVO> providers = new ArrayList<>();
}
