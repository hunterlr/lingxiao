package com.lingxiao.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.workflow.entity.WfCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程分类 Mapper；数量少，只用 MyBatis-Plus 基础方法。
 */
@Mapper
public interface WfCategoryMapper extends BaseMapper<WfCategory> {
}