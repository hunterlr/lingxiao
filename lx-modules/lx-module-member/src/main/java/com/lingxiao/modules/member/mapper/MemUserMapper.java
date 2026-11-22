package com.lingxiao.modules.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.member.entity.MemUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员 Mapper。
 *
 * <p>第一刀全部走 MyBatis-Plus 条件构造器：账号、手机号、邮箱的查询都在
 * Service 里按优先级分别查，避免“一个账号同时命中别人的手机号”这种歧义。
 * 后续出现复杂统计再补显式 SQL。</p>
 */
@Mapper
public interface MemUserMapper extends BaseMapper<MemUser> {
}
