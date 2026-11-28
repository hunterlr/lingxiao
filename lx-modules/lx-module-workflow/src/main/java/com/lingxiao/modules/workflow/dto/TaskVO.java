package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待办 / 已办列表行。
 *
 * <p>标题与发起人来自流程变量，办理人姓名来自变量（发起时随请求写入），
 * 详情页不再回查 sys_user。</p>
 */
@Data
public class TaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;

    private String taskName;

    private String processInstanceId;

    private String processKey;

    private String processName;

    private String title;

    private String initiator;

    private String initiatorName;

    private String assignee;

    private String assigneeName;

    private LocalDateTime createTime;

    /** 已办时间；待办为 null */
    private LocalDateTime endTime;

    private String comment;
}