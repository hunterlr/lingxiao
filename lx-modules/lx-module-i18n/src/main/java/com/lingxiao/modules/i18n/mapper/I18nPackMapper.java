package com.lingxiao.modules.i18n.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.i18n.entity.I18nPack;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语言包版本 Mapper。
 *
 * <p>版本号取当前最大值由 Service 用 selectOne + orderByDesc 完成；
 * 同语言同命名空间只允许一个 {@code status='1'}，由发布逻辑保证。</p>
 */
@Mapper
public interface I18nPackMapper extends BaseMapper<I18nPack> {
}
