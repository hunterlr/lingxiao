package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 标签 Mapper。
 */
@Mapper
public interface CmsTagMapper extends BaseMapper<CmsTag> {

    /** 物理删除；删前由 Service 检查是否还有文章在用。 */
    @Delete("DELETE FROM cms_tag WHERE tag_id = #{tagId}")
    int hardDeleteById(@Param("tagId") Long tagId);
}
