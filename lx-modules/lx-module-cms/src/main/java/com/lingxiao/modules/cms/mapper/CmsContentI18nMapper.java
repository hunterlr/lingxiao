package com.lingxiao.modules.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.cms.entity.CmsContentI18n;
import org.apache.ibatis.annotations.Mapper;

/**
 * CMS 内容多语言 Mapper。
 *
 * <p>只做表读写；语言白名单、状态流转与正文清洗都在 Service 里。</p>
 */
@Mapper
public interface CmsContentI18nMapper extends BaseMapper<CmsContentI18n> {
}