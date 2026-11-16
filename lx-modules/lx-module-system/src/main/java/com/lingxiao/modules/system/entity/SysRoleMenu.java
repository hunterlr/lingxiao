package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单关联（sys_role_menu）。
 *
 * <p>授权的最小单元是菜单 / 按钮，不是接口 URL；接口鉴权靠 perms 码。</p>
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    private Long roleId;
    private Long menuId;
}
