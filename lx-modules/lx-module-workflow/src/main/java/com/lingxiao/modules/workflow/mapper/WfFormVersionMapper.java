package com.lingxiao.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.workflow.entity.WfFormVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单发布快照 Mapper；只插入与按版本读取，不改历史行。
 */
@Mapper
public interface WfFormVersionMapper extends BaseMapper<WfFormVersion> {
}