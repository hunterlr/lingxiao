package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsArticle;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文章 Mapper。列表条件（站点 / 栏目子树 / 标签 / 状态）在服务层拼
 * {@code LambdaQueryWrapper}，标签过滤用 {@code inSql} 走关联表，避免 JOIN 后分页计数失真。
 */
@Mapper
public interface CmsArticleMapper extends BaseMapper<CmsArticle> {

    /** 物理删除；先删标签关联再删正文，由 Service 在同一事务里完成。 */
    @Delete("DELETE FROM cms_article WHERE article_id = #{articleId}")
    int hardDeleteById(@Param("articleId") Long articleId);
}
