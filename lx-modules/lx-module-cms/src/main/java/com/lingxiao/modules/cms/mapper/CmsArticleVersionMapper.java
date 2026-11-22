package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.modules.cms.dto.CmsArticleVersionVO;
import com.lingxiao.modules.cms.entity.CmsArticleVersion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文章内容版本 Mapper。
 *
 * <p>列表 / 最大版本号 / 最新哈希 / 清理都写显式 SQL，便于单测 mock，
 * 也避免在服务层拼 Lambda 条件时依赖 MyBatis-Plus 的实体缓存。</p>
 */
@Mapper
public interface CmsArticleVersionMapper extends BaseMapper<CmsArticleVersion> {

    /** 版本列表：不查正文，按版本号倒序。 */
    @Select("SELECT version_id AS versionId, version_no AS versionNo, change_type AS changeType, "
            + "title AS title, slug AS slug, status AS status, operator_id AS operatorId, "
            + "operator_name AS operatorName, create_time AS createTime "
            + "FROM cms_article_version WHERE article_id = #{articleId} "
            + "ORDER BY version_no DESC")
    Page<CmsArticleVersionVO> selectVersionPage(Page<CmsArticleVersionVO> page,
                                                @Param("articleId") Long articleId);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM cms_article_version WHERE article_id = #{articleId}")
    int selectMaxVersionNo(@Param("articleId") Long articleId);

    /** 最新一条快照的哈希；没有版本时返回 null。 */
    @Select("SELECT content_hash FROM cms_article_version WHERE article_id = #{articleId} "
            + "ORDER BY version_no DESC LIMIT 1")
    String selectLatestHash(@Param("articleId") Long articleId);

    /** 清理最旧版本：删除版本号不大于阈值的记录。 */
    @Delete("DELETE FROM cms_article_version WHERE article_id = #{articleId} "
            + "AND version_no <= #{maxVersionNo}")
    int deleteUpToVersion(@Param("articleId") Long articleId,
                          @Param("maxVersionNo") int maxVersionNo);

    /** 文章彻底删除时清理全部版本。 */
    @Delete("DELETE FROM cms_article_version WHERE article_id = #{articleId}")
    int deleteByArticleId(@Param("articleId") Long articleId);
}
