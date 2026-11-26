package com.lingxiao.modules.i18n.message;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lingxiao.modules.i18n.dto.KeyValueRow;
import com.lingxiao.modules.i18n.mapper.I18nValueMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 数据库译文提供者（带本地缓存）。
 *
 * <p>缓存 {@code expireAfterWrite=5s}：发布 / 回滚会主动清空，
 * 多实例部署时最迟 5 秒也能读到新版本，满足 docs/13 的验收口径。
 * 缓存只存已发布译文，草稿永远不会进 MessageSource。</p>
 */
@Component
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class DatabaseTranslationProvider implements TranslationProvider {

    private final I18nValueMapper valueMapper;

    private final Cache<String, Map<String, String>> cache;

    public DatabaseTranslationProvider(I18nValueMapper valueMapper,
                                       @Value("${lx.module.i18n.cache-seconds:5}") long cacheSeconds) {
        this.valueMapper = valueMapper;
        this.cache = Caffeine.newBuilder()
                .maximumSize(64)
                .expireAfterWrite(Math.max(cacheSeconds, 1), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String find(String languageCode, String keyCode) {
        if (languageCode == null || keyCode == null) {
            return null;
        }
        return published(languageCode).get(keyCode);
    }

    @Override
    public Map<String, String> published(String languageCode) {
        if (languageCode == null) {
            return Collections.emptyMap();
        }
        return cache.get(languageCode, this::load);
    }

    /** 发布 / 回滚后调用；本实例立即生效，其他实例等待 TTL。 */
    public void invalidate() {
        cache.invalidateAll();
    }

    private Map<String, String> load(String languageCode) {
        List<KeyValueRow> rows = valueMapper.selectPublishedRows(languageCode);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>(rows.size());
        for (KeyValueRow row : rows) {
            if (row.getKeyCode() != null) {
                result.put(row.getKeyCode(), row.getValueText());
            }
        }
        return result;
    }
}
