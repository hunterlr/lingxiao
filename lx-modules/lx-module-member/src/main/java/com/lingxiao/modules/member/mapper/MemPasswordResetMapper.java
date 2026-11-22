package com.lingxiao.modules.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.member.entity.MemPasswordReset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员找回密码令牌 Mapper。
 *
 * <p>令牌是一次性审计数据，不进逻辑删除体系；过期记录由后续清理任务处理。</p>
 */
@Mapper
public interface MemPasswordResetMapper extends BaseMapper<MemPasswordReset> {
}
