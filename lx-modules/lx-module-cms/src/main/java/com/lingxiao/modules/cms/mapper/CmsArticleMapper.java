package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsArticle;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
