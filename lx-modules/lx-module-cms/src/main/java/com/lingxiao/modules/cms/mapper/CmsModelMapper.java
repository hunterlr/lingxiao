package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 内容模型 Mapper；条件查询在服务层用 LambdaQueryWrapper 拼。
 */
@Mapper
public interface CmsModelMapper extends BaseMapper<CmsModel> {

    /** 站点删除时物理清理其内置模型，避免遗留孤儿定义。 */
    @Delete("DELETE FROM cms_model WHERE site_id = #{siteId}")
    int hardDeleteBySiteId(@Param("siteId") Long siteId);
}
