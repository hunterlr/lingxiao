package com.lingxiao.modules.i18n.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.i18n.entity.I18nLanguage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语言定义 Mapper。
 *
 * <p>读写都走 MyBatis-Plus；语言数量很少，不写自定义 SQL。</p>
 */
@Mapper
public interface I18nLanguageMapper extends BaseMapper<I18nLanguage> {
}
