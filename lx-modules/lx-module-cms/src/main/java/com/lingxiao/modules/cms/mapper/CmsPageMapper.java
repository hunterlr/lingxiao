package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 单页 Mapper。
 */
@Mapper
public interface CmsPageMapper extends BaseMapper<CmsPage> {

    /** 物理删除；扩展字段由服务层同一事务清理。 */
    @Delete("DELETE FROM cms_page WHERE page_id = #{pageId}")
    int hardDeleteById(@Param("pageId") Long pageId);
}
