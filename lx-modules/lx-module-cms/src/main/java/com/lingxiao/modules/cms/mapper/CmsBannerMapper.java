package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsBanner;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 轮播 Mapper。
 */
@Mapper
public interface CmsBannerMapper extends BaseMapper<CmsBanner> {

    @Delete("DELETE FROM cms_banner WHERE banner_id = #{bannerId}")
    int hardDeleteById(@Param("bannerId") Long bannerId);
}
