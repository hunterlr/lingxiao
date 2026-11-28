package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 发起流程的请求体（后台）。
 *
 * <p>第一刀没有表单引擎，示例流程固定两级审批，所以审批人直接放在请求里；
 * 前端从用户选择器拿到 ID 与姓名一起提交，详情页就不用跨模块查 sys_user。</p>
 */
@Data
public class StartProcessRequest {

    @NotBlank(message = "{valid.workflow.processKey.required}")
    private String processKey;

    @NotBlank(message = "{valid.workflow.title.required}")
    @Size(max = 200, message = "{valid.workflow.title.max}")
    private String title;

    private String remark;

    /** 一级审批人 */
    @NotNull(message = "{valid.workflow.approver.required}")
    private Long approver;

    private String approverName;

    /** 二级审批人；单级流程可不传 */
    private Long approver2;

    private String approver2Name;
}