package com.lingxiao.modules.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联 Mapper。
 *
 * <p>关联表没有业务字段，也不需要逻辑删除，因此直接用注解写 SQL，
 * 不继承 {@code BaseMapper}（MyBatis-Plus 需要主键信息，联合主键表上容易踩坑）。</p>
 */
@Mapper
public interface SysUserRoleMapper {

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM sys_user_role WHERE role_id = #{roleId}")
    int countByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量授权。调用前必须先用 {@code deleteByUserId} 清空，避免重复主键。
     */
    @Insert("<script>"
            + "INSERT INTO sys_user_role (user_id, role_id) VALUES "
            + "<foreach collection='roleIds' item='roleId' separator=','>"
            + "(#{userId}, #{roleId})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}
