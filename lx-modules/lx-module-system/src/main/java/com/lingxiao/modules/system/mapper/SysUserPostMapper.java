package com.lingxiao.modules.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-岗位关联 Mapper。
 */
@Mapper
public interface SysUserPostMapper {

    @Select("SELECT post_id FROM sys_user_post WHERE user_id = #{userId}")
    List<Long> selectPostIdsByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM sys_user_post WHERE post_id = #{postId}")
    int countByPostId(@Param("postId") Long postId);

    @Delete("DELETE FROM sys_user_post WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_post WHERE post_id = #{postId}")
    int deleteByPostId(@Param("postId") Long postId);

    @Insert("<script>"
            + "INSERT INTO sys_user_post (user_id, post_id) VALUES "
            + "<foreach collection='postIds' item='postId' separator=','>"
            + "(#{userId}, #{postId})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
