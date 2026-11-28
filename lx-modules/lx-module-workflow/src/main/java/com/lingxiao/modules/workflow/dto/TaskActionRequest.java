package com.lingxiao.modules.workflow.dto;

import lombok.Data;

/**
 * 办理任务的请求体：同意与驳回共用，只有意见。
 *
 * <p>第一刀不传表单字段、不传目标节点；附件与退回节点等后续刀再加。</p>
 */
@Data
public class TaskActionRequest {

    private String comment;
}