package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.dto.MenuPermRow;
import com.lingxiao.modules.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 菜单 Mapper。
 *
 * <p>三个自定义查询都走“用户 → 角色 → 菜单”的关联，SQL 只用标准语法，
 * 不依赖 MySQL 的 find_in_set / group_concat。</p>
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询用户可见菜单（目录 + 菜单，不含按钮）。
     */
    @Select("SELECT DISTINCT m.* FROM sys_menu m "
            + "INNER JOIN sys_role_menu rm ON m.menu_id = rm.menu_id "
            + "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} "
            + "  AND m.menu_type IN ('M', 'C') "
            + "  AND m.status = '0' AND m.del_flag = '0' "
            + "ORDER BY m.parent_id, m.order_num")
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的权限码及其所属模块。
     *
     * <p>多带一列 {@code module_code} 是为了在模块关闭时过滤权限；
     * 返回 {@link com.lingxiao.modules.system.dto.MenuPermRow} 而不是字符串。</p>
     */
    @Select("SELECT DISTINCT m.perms, m.module_code FROM sys_menu m "
            + "INNER JOIN sys_role_menu rm ON m.menu_id = rm.menu_id "
            + "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} "
            + "  AND m.perms IS NOT NULL AND m.perms <> '' "
            + "  AND m.status = '0' AND m.del_flag = '0'")
    List<MenuPermRow> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的角色标识。
     */
    @Select("SELECT DISTINCT r.role_key FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON r.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} "
            + "  AND r.status = '0' AND r.del_flag = '0'")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    /** 物理删除；删前由 Service 检查子菜单并清理 role_menu */
    @Delete("DELETE FROM sys_menu WHERE menu_id = #{menuId}")
    int hardDeleteById(@Param("menuId") Long menuId);
}
