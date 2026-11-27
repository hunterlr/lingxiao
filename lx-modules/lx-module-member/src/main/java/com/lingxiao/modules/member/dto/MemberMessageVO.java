package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门户消息条目：只含展示字段，不含发送人与内部备注。
 */
@Data
public class MemberMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long msgId;

    /** comment_approved / comment_rejected / system */
    private String msgType;

    private String title;

    private String content;

    /** 关联业务类型（article）与业务 ID，门户可据此跳详情 */
    private String bizType;

    private Long bizId;

    /** 0 未读 / 1 已读 */
    private String readFlag;

    private LocalDateTime readTime;

    private LocalDateTime createTime;
}