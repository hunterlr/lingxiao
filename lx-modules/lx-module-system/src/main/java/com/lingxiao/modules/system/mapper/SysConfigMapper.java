package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统参数 Mapper。
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {

    /** 物理删除；删前由 Service 拒绝删除内置参数（config_type='Y'） */
    @Delete("DELETE FROM sys_config WHERE config_id = #{configId}")
    int hardDeleteById(@Param("configId") Long configId);
}
