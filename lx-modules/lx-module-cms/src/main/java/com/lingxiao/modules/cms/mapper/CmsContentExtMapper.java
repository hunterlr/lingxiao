package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsContentExt;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 内容扩展字段 Mapper。
 */
@Mapper
public interface CmsContentExtMapper extends BaseMapper<CmsContentExt> {

    /** 按 owner 读取扩展；未配置时返回 null。 */
    @Select("SELECT ext_id, site_id, owner_type, owner_id, model_id, ext_json, "
            + "create_by, create_time, update_by, update_time, version "
            + "FROM cms_content_ext WHERE owner_type = #{ownerType} AND owner_id = #{ownerId}")
    CmsContentExt selectByOwner(@Param("ownerType") String ownerType,
                                @Param("ownerId") Long ownerId);

    /** 物理删除扩展，主内容彻底删除时调用。 */
    @Delete("DELETE FROM cms_content_ext WHERE owner_type = #{ownerType} AND owner_id = #{ownerId}")
    int deleteByOwner(@Param("ownerType") String ownerType,
                      @Param("ownerId") Long ownerId);

    /** 删除保护：模型下仍有扩展数据时不允许删模型。 */
    @Select("SELECT COUNT(1) FROM cms_content_ext WHERE model_id = #{modelId}")
    long countByModel(@Param("modelId") Long modelId);
}
