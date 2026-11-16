package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper。
 *
 * <p>常规 CRUD 交给 MyBatis-Plus；复杂查询（数据范围、部门子树）后续再写 XML，
 * 不要把所有 SQL 都堆在注解里。</p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 物理删除。
     *
     * <p>为什么不用 {@code deleteById}：{@link SysUser} 继承的 {@code BaseEntity}
     * 带 {@code @TableLogic}，逻辑删除会和 {@code uk_user_username} 唯一索引冲突
     * ——删掉 test 后再建 test 会报主键/唯一键冲突。系统主数据走物理删除，
     * 删前由 Service 清理关联表。</p>
     */
    @Delete("DELETE FROM sys_user WHERE user_id = #{userId}")
    int hardDeleteById(@Param("userId") Long userId);
}
