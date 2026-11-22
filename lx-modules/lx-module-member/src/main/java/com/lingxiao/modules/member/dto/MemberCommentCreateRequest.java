package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员发表评论。
 *
 * <p>评论是纯文本，长度上限 1000 字；articleId 从路径取，不接受正文里再传。</p>
 */
@Data
public class MemberCommentCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.comment.content.required}")
    @Size(max = 1000, message = "{valid.comment.content.max}")
    private String content;
}
