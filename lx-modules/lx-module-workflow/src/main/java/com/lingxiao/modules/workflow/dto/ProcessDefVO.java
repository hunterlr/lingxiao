package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程定义列表行（后台）。
 *
 * <p>直接由 wf_process_def 映射，额外补一个分类名与“是否有运行中实例”，
 * 供列表页禁用删除按钮。</p>
 */
@Data
public class ProcessDefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long defId;

    private String processKey;

    private String name;

    private String categoryKey;

    private String categoryName;

    private String formKey;

    private String formName;

    private Integer processVersion;

    private String deploymentId;

    private String status;

    private String remark;

    private long runningCount;

    private LocalDateTime createTime;
}