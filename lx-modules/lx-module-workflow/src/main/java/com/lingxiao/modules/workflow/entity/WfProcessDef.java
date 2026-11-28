package com.lingxiao.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流流程定义缓存（wf_process_def）。
 *
 * <p>为什么不直接查 ACT_RE_PROCDEF：引擎表里没有中文名、分类和启停状态，
 * 而且每次都要做版本比较。部署时把结果抄一份到本表，列表页只读本表。</p>
 */
@Data
@TableName("wf_process_def")
public class WfProcessDef implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "def_id", type = IdType.ASSIGN_ID)
    private Long defId;

    private String processKey;

    private String name;

    private String categoryKey;

    /** 引擎里的版本号，列名是 version（不能和乐观锁字段重名） */
    @TableField("version")
    private Integer processVersion;

    private String deploymentId;

    private String definitionId;

    private String resourceName;

    /** 0 启用 / 1 停用 */
    private String status;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private String delFlag;
}