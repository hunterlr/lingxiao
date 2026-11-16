package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysNotice;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 公告 Mapper。
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /** 物理删除；公告没有唯一键，不需要担心删除后重建冲突 */
    @Delete("DELETE FROM sys_notice WHERE notice_id = #{noticeId}")
    int hardDeleteById(@Param("noticeId") Long noticeId);
}
