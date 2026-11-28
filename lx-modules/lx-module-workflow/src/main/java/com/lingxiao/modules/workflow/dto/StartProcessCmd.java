package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 发起流程的内部命令对象。
 *
 * <p>{@code variables} 会原样写进流程变量；示例流程用 {@code approver} /
 * {@code approver2} 两个审批人 ID，以及 {@code title} / {@code remark} 标题说明。</p>
 */
@Data
public class StartProcessCmd {

    private String processKey;

    /** 业务反查键，第一刀用 manual:{uuid}，第二刀接业务单据 */
    private String businessKey;

    /** 发起人（后台用户 ID），同时写进 Flowable 的 START_USER_ID_ */
    private Long userId;

    private String title;

    private String remark;

    private Map<String, Object> variables = new HashMap<>();
}