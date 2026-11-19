package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-部门关联（sys_role_dept）。
 *
 * <p>只在 {@code sys_role.data_scope = '2'}（自定义数据范围）时生效。
 * 一行代表“该角色的可见部门包含该部门”，是<b>精确集合</b>：
 * 勾选父部门不会自动包含子部门，需要下级请显式勾选或改用“本部门及以下”。</p>
 *
 * <p>表没有独立主键，(roleId, deptId) 联合唯一；删除角色 / 部门时由
 * Service 主动清理关联，避免脏数据把数据范围“放大”或“指向空部门”。</p>
 */
@Data
@TableName("sys_role_dept")
public class SysRoleDept {

    private Long roleId;
    private Long deptId;
}
