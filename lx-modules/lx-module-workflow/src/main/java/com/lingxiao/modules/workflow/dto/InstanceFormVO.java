package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实例表单：发起时定格的 schema 版本 + 填写的原始值。
 *
 * <p>办理页与详情抽屉都用它只读渲染；不读当前表单定义，避免历史单“换脸”。</p>
 */
@Data
public class InstanceFormVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String formKey;

    private Integer formVersion;

    private FormSchema schema;

    private Map<String, Object> data = new LinkedHashMap<>();
}