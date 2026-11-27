package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会员站内消息（mem_message）。
 *
 * <p>只存 {@code memberId + bizType / bizId}，不建跨模块外键，也不复制文章标题；
 * 门户要展示“哪篇文章”时再用 CMS 公开接口补。</p>
 *
 * <p>阅读状态是 {@code readFlag}（0 未读 / 1 已读），重复标记已读幂等；
 * 后台删除走逻辑删除（{@link BaseEntity#getDelFlag()}），保留发送记录。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mem_message")
public class MemMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "msg_id", type = IdType.ASSIGN_ID)
    private Long msgId;

    /** 单租户部署固定 0 */
    private Long tenantId;

    /** 收件会员 */
    private Long memberId;

    /** comment_approved / comment_rejected / system */
    private String msgType;

    private String title;

    /** 纯文本，最多 1000 字；门户渲染时由 Vue 转义 */
    private String content;

    /** 关联业务类型，目前只有 article */
    private String bizType;

    private Long bizId;

    /** 0 未读 / 1 已读 */
    private String readFlag;

    private LocalDateTime readTime;
}