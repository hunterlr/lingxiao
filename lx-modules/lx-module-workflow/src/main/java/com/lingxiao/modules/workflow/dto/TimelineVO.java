package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程时间线节点。
 *
 * <p>{@code action}：发起 / 同意 / 驳回 / 待办理。驳回与撤回是终止动作，
 * 会出现在被终止的那个节点上，并带上意见。</p>
 */
@Data
public class TimelineVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String nodeName;

    private String assignee;

    private String assigneeName;

    private String action;

    private String comment;

    private LocalDateTime time;
}