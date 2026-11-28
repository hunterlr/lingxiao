package com.lingxiao.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.workflow.entity.WfProcessDef;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程定义缓存 Mapper。
 *
 * <p>不写自定义 SQL：列表、按 key 取最新版本都用 LambdaQueryWrapper 表达，
 * 表的数据量等于部署次数，很小。</p>
 */
@Mapper
public interface WfProcessDefMapper extends BaseMapper<WfProcessDef> {
}