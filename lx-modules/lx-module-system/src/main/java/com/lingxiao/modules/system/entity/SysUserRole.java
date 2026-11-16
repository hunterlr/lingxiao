package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联（sys_user_role）。
 *
 * <p>联合主键，不继承 BaseEntity；只做关联，不在这里放业务字段。</p>
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    private Long userId;
    private Long roleId;
}
