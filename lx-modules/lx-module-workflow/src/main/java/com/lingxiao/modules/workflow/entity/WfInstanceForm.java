package com.lingxiao.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实例表单快照（wf_instance_form）。
 *
 * <p>发起时定格：表单以后怎么改，历史单看到的还是当时填的值与当时的 schema 版本。</p>
 */
@Data
@TableName("wf_instance_form")
public class WfInstanceForm implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String processInstanceId;

    private String formKey;

    private Integer formVersion;

    /** 发起时填写的原始值，JSON 字符串 */
    private String dataJson;

    private LocalDateTime createTime;
}