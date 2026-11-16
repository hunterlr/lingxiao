package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysDept;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 部门 Mapper。
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /** 物理删除；删前由 Service 检查子部门与用户，并清理闭包表 */
    @Delete("DELETE FROM sys_dept WHERE dept_id = #{deptId}")
    int hardDeleteById(@Param("deptId") Long deptId);
}
