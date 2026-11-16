package com.lingxiao.modules.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.generator.entity.GenTable;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生成表配置 Mapper。
 *
 * <p>没有 XML：列表只按表名 / 注释做 LIKE，用 LambdaQueryWrapper 足够。
 * 复杂查询出现时再加 XML，不要为了“看起来完整”先建一个空 XML。</p>
 */
@Mapper
public interface GenTableMapper extends BaseMapper<GenTable> {
}
