package com.lingxiao.modules.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-菜单关联 Mapper。
 *
 * <p>授权的最小单元是菜单 / 按钮，不是接口 URL；接口鉴权靠 perms 码。</p>
 */
@Mapper
public interface SysRoleMenuMapper {

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}")
    int deleteByMenuId(@Param("menuId") Long menuId);

    @Select("SELECT COUNT(1) FROM sys_role_menu WHERE menu_id = #{menuId}")
    int countByMenuId(@Param("menuId") Long menuId);

    @Insert("<script>"
            + "INSERT INTO sys_role_menu (role_id, menu_id) VALUES "
            + "<foreach collection='menuIds' item='menuId' separator=','>"
            + "(#{roleId}, #{menuId})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);
}
