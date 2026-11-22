package com.lingxiao.modules.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.member.entity.MemLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员点赞 Mapper。
 *
 * <p>点赞关系是“取消即删”的轻量数据，不参与逻辑删除与乐观锁。</p>
 */
@Mapper
public interface MemLikeMapper extends BaseMapper<MemLike> {
}
