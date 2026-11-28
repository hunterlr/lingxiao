package com.lingxiao.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.workflow.entity.WfInstanceForm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实例表单快照 Mapper。
 */
@Mapper
public interface WfInstanceFormMapper extends BaseMapper<WfInstanceForm> {
}