package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询用户当前生效角色（启用且未删除）的数据范围。
     *
     * <p>只查 {@code data_scope} 一列，不加载整个角色对象：数据范围过滤会在
     * 每次用户列表 / 详情请求里调用，没必要带上角色名称、备注等字段。</p>
     *
     * <p>用 {@code SELECT DISTINCT} 是因为多个角色可能配置同一范围；
     * 停用角色不参与过滤，避免“停用角色还在放大数据范围”。</p>
     */
    @Select("SELECT DISTINCT r.data_scope FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON ur.role_id = r.role_id "
            + "WHERE ur.user_id = #{userId} AND r.status = '0' AND r.del_flag = '0'")
    List<String> selectDataScopesByUserId(@Param("userId") Long userId);

    /** 物理删除；删前由 Service 检查是否仍分配给用户并清理 role_menu */
    @Delete("DELETE FROM sys_role WHERE role_id = #{roleId}")
    int hardDeleteById(@Param("roleId") Long roleId);
}
