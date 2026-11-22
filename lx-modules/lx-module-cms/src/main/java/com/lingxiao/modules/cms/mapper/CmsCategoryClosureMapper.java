package com.lingxiao.modules.cms.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 栏目闭包表 Mapper。
 *
 * <p>闭包行由 {@code CmsCategoryService#rebuildClosure} 统一重建：栏目增删改
 * 后按站点全量重算，栏目量级（几十到几百）下比增量维护更不容易错。
 * 所以这里只提供“删站点全部闭包 / 批量插入 / 查祖先与子树”。</p>
 */
@Mapper
public interface CmsCategoryClosureMapper {

    @Insert("INSERT INTO cms_category_closure (category_id, ancestor_id, depth) "
            + "VALUES (#{categoryId}, #{ancestorId}, #{depth})")
    int insertRow(@Param("categoryId") Long categoryId,
                  @Param("ancestorId") Long ancestorId,
                  @Param("depth") Integer depth);

    /**
     * 删掉某站点所有栏目的闭包行，重建前先清空。
     *
     * <p>用 IN 子查询而不是 {@code DELETE ... INNER JOIN}：后者是 MySQL 方言，
     * 迁到 PostgreSQL / 达梦 / 人大金仓时不用再改 SQL。</p>
     */
    @Delete("DELETE FROM cms_category_closure WHERE category_id IN "
            + "(SELECT category_id FROM cms_category WHERE site_id = #{siteId})")
    int deleteBySite(@Param("siteId") Long siteId);

    /** 自身 + 所有祖先，按 depth 升序（自身 depth=0 在最前）。 */
    @Select("SELECT ancestor_id FROM cms_category_closure "
            + "WHERE category_id = #{categoryId} ORDER BY depth")
    List<Long> selectAncestorIds(@Param("categoryId") Long categoryId);

    /** 自身 + 所有后代；用于“按栏目筛选文章时包含子栏目”。 */
    @Select("SELECT category_id FROM cms_category_closure WHERE ancestor_id = #{ancestorId}")
    List<Long> selectSubtreeIds(@Param("ancestorId") Long ancestorId);

    @Select("SELECT COUNT(1) FROM cms_category_closure "
            + "WHERE category_id = #{categoryId} AND ancestor_id = #{ancestorId}")
    int countRelation(@Param("categoryId") Long categoryId,
                      @Param("ancestorId") Long ancestorId);

    /** 删除单个栏目的闭包行（含作为后代的记录）；无子栏目时只会命中自身一行。 */
    @Delete("DELETE FROM cms_category_closure WHERE category_id = #{categoryId}")
    int deleteByCategoryId(@Param("categoryId") Long categoryId);
}
