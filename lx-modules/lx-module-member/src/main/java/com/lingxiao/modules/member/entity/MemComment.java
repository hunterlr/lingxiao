package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会员文章评论（mem_comment）。
 *
 * <p>只存 {@code memberId + articleId}，不建跨模块外键，也不复制文章标题；
 * 文章是否可见由 CMS 门户接口负责。新评论默认待审核，后台通过后门户才展示。</p>
 *
 * <p>删除走逻辑删除（{@link BaseEntity#getDelFlag()}），保留审核记录与备注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mem_comment")
public class MemComment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "comment_id", type = IdType.ASSIGN_ID)
    private Long commentId;

    /** 单租户部署固定 0 */
    private Long tenantId;

    private Long articleId;

    private Long memberId;

    /** 纯文本，最多 1000 字；门户渲染时由 Vue 转义 */
    private String content;

    /** 0 待审核 / 1 已通过 / 2 已拒绝 */
    private String status;

    /** 审核人（平台后台 user_id） */
    private Long auditBy;

    private LocalDateTime auditTime;

    private String auditRemark;

    /** 提交 IP，仅用于排查与风控 */
    private String createIp;
}
