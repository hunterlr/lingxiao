package com.lingxiao.modules.i18n.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.i18n.entity.I18nKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 翻译键 Mapper。
 *
 * <p>分页 / 筛选由 Service 用 LambdaQueryWrapper 组合，避免每个条件写一条 SQL。</p>
 */
@Mapper
public interface I18nKeyMapper extends BaseMapper<I18nKey> {
}
