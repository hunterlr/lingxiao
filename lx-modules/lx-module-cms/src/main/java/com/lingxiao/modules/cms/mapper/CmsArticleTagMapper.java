package com.lingxiao.modules.cms.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章-标签关联 Mapper。
 *
 * <p>没有实体：关联表只有两列，直接写 SQL 比造一个只有两个字段的实体更清楚。
 * 替换标签时先按文章删、再批量插，放在同一事务里。</p>
 */
@Mapper
public interface CmsArticleTagMapper {

    @Delete("DELETE FROM cms_article_tag WHERE article_id = #{articleId}")
    int deleteByArticleId(@Param("articleId") Long articleId);

    @Delete("DELETE FROM cms_article_tag WHERE tag_id = #{tagId}")
    int deleteByTagId(@Param("tagId") Long tagId);

    @Insert("<script>"
            + "INSERT INTO cms_article_tag (article_id, tag_id) VALUES "
            + "<foreach collection='tagIds' item='tagId' separator=','>"
            + "(#{articleId}, #{tagId})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("articleId") Long articleId, @Param("tagIds") List<Long> tagIds);

    @Select("SELECT tag_id FROM cms_article_tag WHERE article_id = #{articleId}")
    List<Long> selectTagIds(@Param("articleId") Long articleId);

    /** 标签是否仍被文章引用；删除标签前必须为 0。 */
    @Select("SELECT COUNT(1) FROM cms_article_tag WHERE tag_id = #{tagId}")
    int countByTagId(@Param("tagId") Long tagId);
}
