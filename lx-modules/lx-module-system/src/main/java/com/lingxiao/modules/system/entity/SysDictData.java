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
 * 字典数据（sys_dict_data）。
 *
 * <p>前端下拉框按 {@code dictType} 一次取全部，所以查询条件必须带类型，
 * 不做“全表字典”接口。</p>
 *
 * <p>{@code isDefault} 只影响前端默认选中项，不做业务强制。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    @TableId(value = "dict_code", type = IdType.ASSIGN_ID)
    private Long dictCode;

    private Integer dictSort;

    @NotBlank(message = "{valid.dictData.label.required}")
    @Size(max = 100, message = "{valid.dictData.label.max}")
    private String dictLabel;

    @NotBlank(message = "{valid.dictData.value.required}")
    @Size(max = 100, message = "{valid.dictData.value.max}")
    private String dictValue;

    @NotBlank(message = "{valid.dictData.type.required}")
    @Size(max = 100, message = "{valid.dictData.type.max}")
    private String dictType;

    private String cssClass;
    private String listClass;

    /** Y 默认 / N 非默认 */
    private String isDefault;

    /** 0 正常 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
