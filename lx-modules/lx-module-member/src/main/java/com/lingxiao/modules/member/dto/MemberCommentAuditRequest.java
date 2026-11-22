package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 后台审核评论。
 *
 * <p>{@code status} 只接受 1 已通过 / 2 已拒绝；拒绝时 {@code remark} 可写原因，
 * 但不会展示到门户。</p>
 */
@Data
public class MemberCommentAuditRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String status;

    @Size(max = 255, message = "{valid.remark.max}")
    private String remark;
}
