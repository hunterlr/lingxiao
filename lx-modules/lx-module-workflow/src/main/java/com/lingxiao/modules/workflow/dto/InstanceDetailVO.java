package com.lingxiao.modules.workflow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程实例详情：列表字段 + 说明 + 时间线。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InstanceDetailVO extends InstanceVO {

    private static final long serialVersionUID = 1L;

    private String initiator;

    private String initiatorName;

    private List<TimelineVO> timeline = new ArrayList<>();

    /** 用户 ID → 姓名，表单只读展示用 */
    private Map<String, String> assigneeNames = new LinkedHashMap<>();
}