package com.lingxiao.modules.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.member.entity.MemComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员评论 Mapper。
 *
 * <p>评论是业务数据，删除由逻辑删除完成，不做物理删除。</p>
 */
@Mapper
public interface MemCommentMapper extends BaseMapper<MemComment> {
}
