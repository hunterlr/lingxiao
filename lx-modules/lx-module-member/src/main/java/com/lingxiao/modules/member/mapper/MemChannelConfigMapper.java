package com.lingxiao.modules.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.member.entity.MemChannelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员消息通道配置 Mapper。
 *
 * <p>只有短信 / 邮件两行，直接按 {@code channel_type} 查询，不做缓存。</p>
 */
@Mapper
public interface MemChannelConfigMapper extends BaseMapper<MemChannelConfig> {
}
