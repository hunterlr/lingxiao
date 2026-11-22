package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsCategory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 栏目 Mapper。树的父子关系在 {@code parent_id}，子树查询走闭包表
 * {@link CmsCategoryClosureMapper}。
 */
@Mapper
public interface CmsCategoryMapper extends BaseMapper<CmsCategory> {

    /**
     * 物理删除。栏目没有文章、没有子栏目时才允许删，
     * 由 Service 先校验；逻辑删除会让 {@code (site_id, category_key)}
     * 唯一键一直占着，重建同名栏目会失败。
     */
    @Delete("DELETE FROM cms_category WHERE category_id = #{categoryId}")
    int hardDeleteById(@Param("categoryId") Long categoryId);
}
