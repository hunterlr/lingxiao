package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysDictType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 字典类型 Mapper。
 */
@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {

    /** 物理删除；删前由 Service 检查是否仍有字典数据 */
    @Delete("DELETE FROM sys_dict_type WHERE dict_id = #{dictId}")
    int hardDeleteById(@Param("dictId") Long dictId);
}
