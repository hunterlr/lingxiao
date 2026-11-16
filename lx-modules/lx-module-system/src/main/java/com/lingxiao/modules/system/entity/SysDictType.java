package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 字典类型（sys_dict_type）。
 *
 * <p>一个类型下挂多条字典数据（sys_dict_data）。类型被数据引用时不允许删除，
 * 避免出现“数据还在、类型没了”的悬空字典。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    @TableId(value = "dict_id", type = IdType.ASSIGN_ID)
    private Long dictId;

    @NotBlank(message = "{valid.dictType.name.required}")
    @Size(max = 100, message = "{valid.dictType.name.max}")
    private String dictName;

    @NotBlank(message = "{valid.dictType.type.required}")
    @Size(max = 100, message = "{valid.dictType.type.max}")
    private String dictType;

    /** 0 正常 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
