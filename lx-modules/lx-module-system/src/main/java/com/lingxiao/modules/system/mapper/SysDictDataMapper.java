package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysDictData;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 字典数据 Mapper。
 */
@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {

    /** 物理删除单条 */
    @Delete("DELETE FROM sys_dict_data WHERE dict_code = #{dictCode}")
    int hardDeleteById(@Param("dictCode") Long dictCode);

    /** 物理删除某类型下全部数据（仅在“删类型并连带删数据”时使用） */
    @Delete("DELETE FROM sys_dict_data WHERE dict_type = #{dictType}")
    int hardDeleteByType(@Param("dictType") String dictType);
}
