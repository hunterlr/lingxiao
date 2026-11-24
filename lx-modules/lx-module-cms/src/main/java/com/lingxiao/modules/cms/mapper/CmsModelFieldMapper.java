package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsModelField;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 模型字段 Mapper。
 */
@Mapper
public interface CmsModelFieldMapper extends BaseMapper<CmsModelField> {

    /** 物理删除字段定义；扩展 JSON 中的历史值保留，不做破坏性清理。 */
    @Delete("DELETE FROM cms_model_field WHERE field_id = #{fieldId}")
    int hardDeleteById(@Param("fieldId") Long fieldId);

    /** 站点删除时物理清理字段定义。 */
    @Delete("DELETE FROM cms_model_field WHERE site_id = #{siteId}")
    int hardDeleteBySiteId(@Param("siteId") Long siteId);
}
