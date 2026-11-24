package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsBlock;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 区块 Mapper。
 */
@Mapper
public interface CmsBlockMapper extends BaseMapper<CmsBlock> {

    @Delete("DELETE FROM cms_block WHERE block_id = #{blockId}")
    int hardDeleteById(@Param("blockId") Long blockId);
}
