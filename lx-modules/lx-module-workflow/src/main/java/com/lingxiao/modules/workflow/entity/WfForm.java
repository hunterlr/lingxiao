package com.lingxiao.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流表单定义（wf_form）。
 *
 * <p>{@code schemaJson} 是“正在编辑的版本”，{@code version} 是已发布版本号（0=从未发布）。
 * 发起流程读的是 {@link WfFormVersion} 里的快照，所以改完不发布不会影响新单。</p>
 */
@Data
@TableName("wf_form")
public class WfForm implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "form_id", type = IdType.ASSIGN_ID)
    private Long formId;

    private String formKey;

    private String name;

    private String schemaJson;

    /** 已发布版本，0 表示从未发布 */
    private Integer version;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private String delFlag;
}