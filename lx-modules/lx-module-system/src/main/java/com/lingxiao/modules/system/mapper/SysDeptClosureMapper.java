package com.lingxiao.modules.system.mapper;

import com.lingxiao.modules.system.entity.SysDeptClosure;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门闭包表 Mapper。
 *
 * <p>三个核心查询：</p>
 * <ul>
 *   <li>{@link #selectAncestors}：某部门的全部祖先（含自己，depth 从 0 递增）；</li>
 *   <li>{@link #selectSubtree}：某部门的整棵子树（含自己）；</li>
 *   <li>{@link #deleteByDescendant}：删除指向某节点的全部祖先链接。</li>
 * </ul>
 *
 * <p>所有 SQL 只用标准语法，不依赖 MySQL 方言。</p>
 */
@Mapper
public interface SysDeptClosureMapper {

    @Select("SELECT ancestor_id, descendant_id, depth FROM sys_dept_closure "
            + "WHERE descendant_id = #{deptId} ORDER BY depth")
    List<SysDeptClosure> selectAncestors(@Param("deptId") Long deptId);

    @Select("SELECT ancestor_id, descendant_id, depth FROM sys_dept_closure "
            + "WHERE ancestor_id = #{deptId} ORDER BY depth")
    List<SysDeptClosure> selectSubtree(@Param("deptId") Long deptId);

    @Insert("INSERT INTO sys_dept_closure (ancestor_id, descendant_id, depth) "
            + "VALUES (#{ancestorId}, #{descendantId}, #{depth})")
    int insertOne(SysDeptClosure closure);

    @Delete("DELETE FROM sys_dept_closure WHERE descendant_id = #{deptId}")
    int deleteByDescendant(@Param("deptId") Long deptId);

    @Delete("DELETE FROM sys_dept_closure WHERE ancestor_id = #{deptId} OR descendant_id = #{deptId}")
    int deleteByNode(@Param("deptId") Long deptId);
}
