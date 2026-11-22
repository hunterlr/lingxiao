package com.lingxiao.modules.cms.mapper;

import com.lingxiao.modules.cms.dto.ArticleTagRow;
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

    /**
     * 一批文章的标签关联，供门户列表一次性回填标签。
     *
     * <p>用 IN 查询而不是逐篇查，避免一页 N+1；调用方保证 articleIds 非空。</p>
     */
    @Select("<script>"
            + "SELECT article_id AS articleId, tag_id AS tagId FROM cms_article_tag "
            + "WHERE article_id IN "
            + "<foreach collection='articleIds' item='articleId' open='(' separator=',' close=')'>"
            + "#{articleId}"
            + "</foreach>"
            + "</script>")
    List<ArticleTagRow> selectRowsByArticleIds(@Param("articleIds") List<Long> articleIds);

    /** 标签是否仍被文章引用；删除标签前必须为 0。 */
    @Select("SELECT COUNT(1) FROM cms_article_tag WHERE tag_id = #{tagId}")
    int countByTagId(@Param("tagId") Long tagId);
}
