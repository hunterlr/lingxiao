package com.lingxiao.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单发布快照（wf_form_version）。
 *
 * <p>只增不改：发布一次写一行，历史实例按当时版本渲染，不随表单改版漂移。</p>
 */
@Data
@TableName("wf_form_version")
public class WfFormVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "version_id", type = IdType.ASSIGN_ID)
    private Long versionId;

    private Long formId;

    private Integer version;

    private String schemaJson;

    private Long createBy;

    private LocalDateTime createTime;
}