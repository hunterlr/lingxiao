package com.lingxiao.modules.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-部门关联 Mapper（自定义数据范围）。
 *
 * <p>不继承 {@code BaseMapper}：这张表只有两个字段，没有审计列，
 * 用显式 SQL 比 MyBatis-Plus 的通用 CRUD 更清楚。</p>
 */
@Mapper
public interface SysRoleDeptMapper {

    /** 角色已勾选的部门 ID，详情回显用。 */
    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId} ORDER BY dept_id")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 用户所有启用角色中、自定义范围（data_scope='2'）的部门并集。
     *
     * <p>停用 / 已删除角色不参与；返回 {@code DISTINCT}，多角色重复勾选不放大结果。
     * 空结果表示“配置了自定义范围但一个部门都没勾”，调用方必须收紧为仅本人。</p>
     */
    @Select("SELECT DISTINCT rd.dept_id FROM sys_role_dept rd "
            + "INNER JOIN sys_role r ON r.role_id = rd.role_id "
            + "INNER JOIN sys_user_role ur ON ur.role_id = r.role_id "
            + "WHERE ur.user_id = #{userId} AND r.data_scope = '2' "
            + "AND r.status = '0' AND r.del_flag = '0' "
            + "ORDER BY rd.dept_id")
    List<Long> selectDeptIdsByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 删除部门时清理关联，避免留下指向不存在部门的范围。 */
    @Delete("DELETE FROM sys_role_dept WHERE dept_id = #{deptId}")
    int deleteByDeptId(@Param("deptId") Long deptId);

    @Insert("<script>"
            + "INSERT INTO sys_role_dept (role_id, dept_id) VALUES "
            + "<foreach collection='deptIds' item='deptId' separator=','>"
            + "(#{roleId}, #{deptId})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("roleId") Long roleId, @Param("deptIds") List<Long> deptIds);
}
