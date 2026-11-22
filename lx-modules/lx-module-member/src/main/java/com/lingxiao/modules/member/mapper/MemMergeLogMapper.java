package com.lingxiao.modules.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.member.entity.MemMergeLog;
import org.apache.ibatis.annotations.Mapper;

/** 会员账号合并审计 Mapper；只追加，不修改。 */
@Mapper
public interface MemMergeLogMapper extends BaseMapper<MemMergeLog> {
}
