package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsNav;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 导航 Mapper。
 */
@Mapper
public interface CmsNavMapper extends BaseMapper<CmsNav> {

    /** 物理删除导航；导航项由服务层同一事务清理。 */
    @Delete("DELETE FROM cms_nav WHERE nav_id = #{navId}")
    int hardDeleteById(@Param("navId") Long navId);
}
