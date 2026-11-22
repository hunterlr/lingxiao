package com.lingxiao.modules.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.member.entity.MemLevel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员等级 Mapper。
 *
 * <p>等级数量很少，下拉与默认等级都直接查列表后在 Service 里取第一条，
 * 不用 {@code LIMIT 1}，避免引入 MySQL 方言。</p>
 */
@Mapper
public interface MemLevelMapper extends BaseMapper<MemLevel> {
}
