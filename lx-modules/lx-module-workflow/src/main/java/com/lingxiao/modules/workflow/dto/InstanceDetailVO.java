package com.lingxiao.modules.workflow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

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
}