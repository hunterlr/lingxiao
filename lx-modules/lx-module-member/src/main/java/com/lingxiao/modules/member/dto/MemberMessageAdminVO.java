package com.lingxiao.modules.member.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台消息列表条目：在门户字段上补充收件会员信息，便于按人筛选与核对。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberMessageAdminVO extends MemberMessageVO {

    private static final long serialVersionUID = 1L;

    private Long memberId;

    private String username;

    private String nickname;
}