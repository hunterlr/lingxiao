package com.lingxiao.modules.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.generator.entity.GenTableColumn;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 生成字段配置 Mapper。
 *
 * <p>删除表配置时按 {@code table_id} 批量物理删除子行；
 * 用显式 SQL 而不是 {@code delete(wrapper)}，是让“删的是谁”一眼可见。</p>
 */
@Mapper
public interface GenTableColumnMapper extends BaseMapper<GenTableColumn> {

    @Delete("DELETE FROM gen_table_column WHERE table_id = #{tableId}")
    int deleteByTableId(@Param("tableId") Long tableId);
}
