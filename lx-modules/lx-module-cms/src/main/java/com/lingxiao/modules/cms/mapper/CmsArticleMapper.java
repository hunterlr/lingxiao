package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.modules.cms.dto.CmsArticleRecycleVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 文章 Mapper。列表条件（站点 / 栏目子树 / 标签 / 状态）在服务层拼
 * {@code LambdaQueryWrapper}，标签过滤用 EXISTS 子查询，避免 JOIN 后分页计数失真。
 */
@Mapper
public interface CmsArticleMapper extends BaseMapper<CmsArticle> {

    /** 物理删除；先删标签关联再删正文，由 Service 在同一事务里完成。 */
    @Delete("DELETE FROM cms_article WHERE article_id = #{articleId}")
    int hardDeleteById(@Param("articleId") Long articleId);

    /**
     * 门户阅读量 +1。
     *
     * <p>用单条 UPDATE 自增，不先查后写，避免并发访问时互相覆盖。
     * SQL 只用标准语法，MySQL / PostgreSQL / 达梦 / 金仓通用。</p>
     */
    @Update("UPDATE cms_article SET view_count = view_count + 1 WHERE article_id = #{articleId}")
    int increaseViewCount(@Param("articleId") Long articleId);

    // ==================== 回收站 ====================

    /**
     * 逻辑删除：只改 del_flag 并记录删除审计，标签关联保留，恢复后标签不丢。
     *
     * <p>MyBatis-Plus 的 {@code deleteById} 不会填删除人 / 删除时间，
     * 这里用显式 SQL 一次写完，避免“进回收站但不知道谁删的”。</p>
     */
    @Update("UPDATE cms_article SET del_flag = '1', delete_by = #{deleteBy}, "
            + "delete_by_name = #{deleteByName}, delete_time = #{deleteTime}, "
            + "update_by = #{deleteBy}, update_time = #{deleteTime}, version = version + 1 "
            + "WHERE article_id = #{articleId} AND del_flag = '0'")
    int softDeleteById(@Param("articleId") Long articleId,
                       @Param("deleteBy") Long deleteBy,
                       @Param("deleteByName") String deleteByName,
                       @Param("deleteTime") LocalDateTime deleteTime);

    /**
     * 恢复：del_flag 归 0，状态统一回到草稿，避免恢复即对外发布。
     *
     * <p>publish_time 保留：它表示“首次发布时间”，重新发布时沿用，
     * 不会因为进过一次回收站就把历史发布时间清掉。</p>
     */
    @Update("UPDATE cms_article SET del_flag = '0', status = '0', "
            + "delete_by = NULL, delete_by_name = NULL, delete_time = NULL, "
            + "update_by = #{updateBy}, update_time = #{updateTime}, version = version + 1 "
            + "WHERE article_id = #{articleId} AND del_flag = '1'")
    int restoreById(@Param("articleId") Long articleId,
                    @Param("updateBy") Long updateBy,
                    @Param("updateTime") LocalDateTime updateTime);

    /** 彻底删除：只允许删除回收站里的记录，防止绕过软删除直接物理删。 */
    @Delete("DELETE FROM cms_article WHERE article_id = #{articleId} AND del_flag = '1'")
    int hardDeleteDeletedById(@Param("articleId") Long articleId);

    /** 读回收站记录；普通 selectById 会被逻辑删除插件过滤，因此单独查。 */
    @Select("SELECT article_id, site_id, category_id, title, slug, summary, cover, "
            + "author_id, author_name, source, source_url, status, publish_time, view_count, "
            + "sort, create_by, create_time, update_by, update_time, version, remark, "
            + "delete_by, delete_by_name, delete_time "
            + "FROM cms_article WHERE article_id = #{articleId} AND del_flag = '1'")
    CmsArticle selectDeletedById(@Param("articleId") Long articleId);

    /**
     * 回收站分页。
     *
     * <p>关键词由 Service 预处理成 {@code %kw%}，避免在 SQL 里拼
     * {@code CONCAT}，保持多库可移植。站点 / 栏目用 LEFT JOIN：极端情况下
     * 栏目被手工删掉，回收站列表仍能显示而不是整行消失。</p>
     */
    @Select("<script>"
            + "SELECT a.article_id AS articleId, a.site_id AS siteId, s.site_name AS siteName, "
            + "a.category_id AS categoryId, c.category_name AS categoryName, "
            + "a.title AS title, a.slug AS slug, a.status AS status, "
            + "a.delete_by AS deleteBy, a.delete_by_name AS deleteByName, a.delete_time AS deleteTime "
            + "FROM cms_article a "
            + "LEFT JOIN cms_site s ON s.site_id = a.site_id "
            + "LEFT JOIN cms_category c ON c.category_id = a.category_id "
            + "WHERE a.del_flag = '1' "
            + "<if test='siteId != null'> AND a.site_id = #{siteId}</if> "
            + "<if test='keyword != null'> AND (a.title LIKE #{keyword} OR a.slug LIKE #{keyword})</if> "
            + "ORDER BY a.delete_time DESC, a.article_id DESC"
            + "</script>")
    Page<CmsArticleRecycleVO> selectRecyclePage(Page<CmsArticleRecycleVO> page,
                                                @Param("siteId") Long siteId,
                                                @Param("keyword") String keyword);

    /** slug 唯一性判断包含回收站：回收中的 slug 不允许被新文章占用，恢复才不会撞车。 */
    @Select("<script>SELECT COUNT(1) FROM cms_article "
            + "WHERE site_id = #{siteId} AND slug = #{slug}"
            + "<if test='excludeId != null'> AND article_id &lt;&gt; #{excludeId}</if>"
            + "</script>")
    long countBySlugIncludingDeleted(@Param("siteId") Long siteId,
                                     @Param("slug") String slug,
                                     @Param("excludeId") Long excludeId);

    /** 删除保护：站点下只要还有文章（含回收站）就不允许删站点。 */
    @Select("SELECT COUNT(1) FROM cms_article WHERE site_id = #{siteId}")
    long countBySiteIncludingDeleted(@Param("siteId") Long siteId);

    /** 删除保护：栏目下只要还有文章（含回收站）就不允许删栏目。 */
    @Select("SELECT COUNT(1) FROM cms_article WHERE category_id = #{categoryId}")
    long countByCategoryIncludingDeleted(@Param("categoryId") Long categoryId);
}
