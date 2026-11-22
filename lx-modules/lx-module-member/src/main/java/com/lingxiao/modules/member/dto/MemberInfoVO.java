package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员个人中心信息（会员端只读视图）。
 *
 * <p>返回会员自己的完整资料；后台列表另走 {@code MemUser} + 等级名，
 * 不复用本 VO，避免以后给会员端加“脱敏字段”时误伤后台。</p>
 */
@Data
public class MemberInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long memberId;

    private String username;

    private String nickname;

    private String avatar;

    /** 0 未设置密码（第三方建号）/ 1 已设置 */
    private String passwordSet;

    private String mobile;

    private String email;

    private String sex;

    private Long levelId;

    private String levelName;

    private Integer points;

    private Integer growth;

    private String status;

    private LocalDateTime registerTime;

    private LocalDateTime loginTime;
}
