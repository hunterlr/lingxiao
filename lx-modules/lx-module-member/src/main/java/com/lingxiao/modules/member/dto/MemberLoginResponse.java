package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 会员登录 / 注册成功后的会话信息。
 *
 * <p>{@code tokenName} 固定为 {@code lx-member-token}，前端不要写死，
 * 以响应为准；后续做多域共存时 tokenName 可能按场景变化。</p>
 */
@Data
public class MemberLoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tokenName;

    private String tokenValue;

    /** Token 剩余有效期（秒） */
    private long tokenTimeout;

    private Long memberId;

    private String username;

    private String nickname;

    private String avatar;

    private Long levelId;

    private String levelName;

    private Integer points;

    private Integer growth;
}
