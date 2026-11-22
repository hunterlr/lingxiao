package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门户公开评论条目。
 *
 * <p>只返回展示所需字段：不暴露会员 ID、手机号、邮箱等账号信息；
 * 昵称与头像在评论写入时按 {@code mem_user} 回填。</p>
 */
@Data
public class MemberCommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long commentId;

    private Long articleId;

    private String content;

    private String nickname;

    private String avatar;

    private LocalDateTime createTime;
}
