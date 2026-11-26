package com.lingxiao.modules.i18n.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.i18n.entity.I18nMissingLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 缺失翻译记录 Mapper。
 *
 * <p>定时任务批量落库，用 {@code ON DUPLICATE KEY UPDATE} 累加命中次数；
 * 唯一键 {@code (tenant_id, language_code, namespace, key_code)} 保证只有一行。</p>
 */
@Mapper
public interface I18nMissingLogMapper extends BaseMapper<I18nMissingLog> {

    @Insert("INSERT INTO i18n_missing_log "
            + "(log_id, tenant_id, language_code, namespace, key_code, hit_count, first_seen, last_seen, sample_locale) "
            + "VALUES (#{logId}, #{tenantId}, #{languageCode}, #{namespace}, #{keyCode}, "
            + "#{hitCount}, #{firstSeen}, #{lastSeen}, #{sampleLocale}) "
            + "ON DUPLICATE KEY UPDATE "
            + "hit_count = hit_count + VALUES(hit_count), "
            + "last_seen = VALUES(last_seen), "
            + "sample_locale = VALUES(sample_locale)")
    int upsert(I18nMissingLog log);
}
