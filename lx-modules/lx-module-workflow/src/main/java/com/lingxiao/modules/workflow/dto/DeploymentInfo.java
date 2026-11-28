package com.lingxiao.modules.workflow.dto;

import lombok.Data;

/**
 * 引擎部署结果。
 *
 * <p>只暴露平台需要的字段，避免把 Flowable 的 Deployment / ProcessDefinition
 * 实体直接传到业务层。</p>
 */
@Data
public class DeploymentInfo {

    private String deploymentId;

    private String processKey;

    private String name;

    private Integer version;

    private String definitionId;

    private String resourceName;
}