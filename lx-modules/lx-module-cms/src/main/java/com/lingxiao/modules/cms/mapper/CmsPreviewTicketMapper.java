package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsPreviewTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 草稿预览票据 Mapper。
 */
@Mapper
public interface CmsPreviewTicketMapper extends BaseMapper<CmsPreviewTicket> {

    /**
     * 原子消费票据：只有未使用且未过期的票据能被占领。
     *
     * <p>用条件 UPDATE 而不是“先查后改”，多标签页同时打开预览链接时
     * 只有第一个请求成功，其余按票据失效处理。</p>
     *
     * @return 影响行数，1 表示本次消费成功
     */
    @Update("UPDATE cms_preview_ticket SET used = '1', update_time = NOW(3) "
            + "WHERE ticket = #{ticket} AND used = '0' AND expire_time > NOW(3) "
            + "AND del_flag = '0'")
    int consume(@Param("ticket") String ticket);
}
