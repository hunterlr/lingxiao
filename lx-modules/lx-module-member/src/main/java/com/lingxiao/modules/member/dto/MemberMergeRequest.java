package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 后台会员账号合并请求。
 *
 * <p>{@code sourceMemberId} 是准备停用的旧账号，{@code targetMemberId} 是保留的账号；
 * 收藏、点赞、评论与第三方绑定都会迁移到目标账号。</p>
 */
@Data
public class MemberMergeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "{valid.member.merge.source.required}")
    private Long sourceMemberId;

    @NotNull(message = "{valid.member.merge.target.required}")
    private Long targetMemberId;

    /** 操作备注，写入合并审计 */
    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
