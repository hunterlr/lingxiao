package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsNavItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 导航项 Mapper。
 */
@Mapper
public interface CmsNavItemMapper extends BaseMapper<CmsNavItem> {

    @Delete("DELETE FROM cms_nav_item WHERE nav_id = #{navId}")
    int deleteByNavId(@Param("navId") Long navId);

    @Delete("DELETE FROM cms_nav_item WHERE item_id = #{itemId}")
    int hardDeleteById(@Param("itemId") Long itemId);

    /** 导航项数量：删除导航 / 重建时校验用。 */
    @Select("SELECT COUNT(1) FROM cms_nav_item WHERE nav_id = #{navId} AND del_flag = '0'")
    long countByNavId(@Param("navId") Long navId);

    /** 目标引用数量：删除单页 / 栏目时避免留下死链。 */
    @Select("SELECT COUNT(1) FROM cms_nav_item WHERE target_type = #{targetType} "
            + "AND target_id = #{targetId} AND del_flag = '0'")
    long countByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}
