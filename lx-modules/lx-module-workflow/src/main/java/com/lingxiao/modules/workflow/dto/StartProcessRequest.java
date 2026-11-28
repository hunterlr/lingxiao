package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;

/**
 * 发起流程的请求体（后台）。
 *
 * <p>第二刀起不再有固定的 approver / approver2 字段：审批人、金额、事由都由
 * 流程绑定的动态表单提供，字段 key 即流程变量名。</p>
 */
@Data
public class StartProcessRequest {

    @NotBlank(message = "{valid.workflow.processKey.required}")
    private String processKey;

    @NotBlank(message = "{valid.workflow.title.required}")
    @Size(max = 200, message = "{valid.workflow.title.max}")
    private String title;

    private String remark;

    /** 表单值，按 schema 校验后才写进流程变量 */
    private Map<String, Object> formData = new HashMap<>();

    /** 用户 ID → 姓名；详情页按它显示审批人，不跨模块回查 sys_user */
    private Map<String, String> assigneeNames = new HashMap<>();
}