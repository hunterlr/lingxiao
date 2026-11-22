package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/** 会员账号合并执行结果。 */
@Data
public class MemberMergeResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mergeId;
    private Long sourceMemberId;
    private Long targetMemberId;

    private int favoriteMoved;
    private int favoriteSkipped;
    private int likeMoved;
    private int likeSkipped;
    private int commentMoved;
    private int socialMoved;
    private int socialSkipped;
}
