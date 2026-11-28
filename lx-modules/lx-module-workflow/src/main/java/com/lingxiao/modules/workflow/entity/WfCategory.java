package com.lingxiao.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流流程分类（wf_category）。
 *
 * <p>只做后台列表分组，不参与引擎逻辑；流程 XML 里的 category 仍由部署时传入。</p>
 */
@Data
@TableName("wf_category")
public class WfCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "category_id", type = IdType.INPUT)
    private Long categoryId;

    private String categoryKey;

    private String name;

    private Integer sort;

    /** 0 启用 / 1 停用 */
    private String status;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private String delFlag;
}