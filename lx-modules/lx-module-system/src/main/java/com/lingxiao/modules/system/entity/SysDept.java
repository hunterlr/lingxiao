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
 * 部门（sys_dept）。
 *
 * <p>树结构用 {@code parent_id} 表达，层级查询用闭包表 {@code sys_dept_closure}，
 * 不使用 MySQL 的 {@code find_in_set}，为多库兼容留路。</p>
 *
 * <p>删除前置条件：没有子部门、没有用户，否则拒绝删除（防止出现孤儿数据）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    @TableId(value = "dept_id", type = IdType.ASSIGN_ID)
    private Long deptId;

    /** 上级部门，0 表示根部门 */
    private Long parentId;

    @NotBlank(message = "{valid.dept.name.required}")
    @Size(max = 64, message = "{valid.dept.name.max}")
    private String deptName;

    private Integer orderNum;

    private String leader;
    private String phone;
    private String email;

    /** 0 正常 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
