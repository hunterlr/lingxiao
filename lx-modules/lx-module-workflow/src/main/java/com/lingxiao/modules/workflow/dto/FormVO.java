package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单列表 / 详情。
 *
 * <p>{@code schema} 是正在编辑的版本；{@code version} 是已发布版本号。
 * 列表页可以据此提示“有未发布改动”。</p>
 */
@Data
public class FormVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long formId;

    private String formKey;

    private String name;

    private String remark;

    private Integer version;

    /** 是否有未发布的改动：草稿 schema 与已发布快照不同 */
    private boolean changed;

    private FormSchema schema;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}