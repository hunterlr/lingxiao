package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysPost;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 岗位 Mapper。
 */
@Mapper
public interface SysPostMapper extends BaseMapper<SysPost> {

    /** 物理删除；删前由 Service 检查是否有用户占用并清理 user_post */
    @Delete("DELETE FROM sys_post WHERE post_id = #{postId}")
    int hardDeleteById(@Param("postId") Long postId);
}
