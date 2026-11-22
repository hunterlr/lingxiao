package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsSite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 站点 Mapper。CRUD 全部走 MyBatis-Plus，无自定义查询。
 */
@Mapper
public interface CmsSiteMapper extends BaseMapper<CmsSite> {

    /** 物理删除；删前由 Service 检查站点下是否还有栏目 / 文章。 */
    @Delete("DELETE FROM cms_site WHERE site_id = #{siteId}")
    int hardDeleteById(@Param("siteId") Long siteId);
}
