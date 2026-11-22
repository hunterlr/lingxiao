package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台评论审核列表条目。
 *
 * <p>带会员账号与审核信息，供运营判断；门户公开接口不会返回这些字段。</p>
 */
@Data
public class MemberCommentAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long commentId;

    private Long articleId;

    private Long memberId;

    /** 登录账号，便于运营定位 */
    private String username;

    private String nickname;

    private String avatar;

    private String content;

    /** 0 待审核 / 1 已通过 / 2 已拒绝 */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime auditTime;

    private String auditRemark;
}
