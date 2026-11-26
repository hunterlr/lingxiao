package com.lingxiao.modules.i18n.message;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.lingxiao.modules.i18n.entity.I18nMissingLog;
import com.lingxiao.modules.i18n.mapper.I18nMissingLogMapper;
import com.lingxiao.modules.i18n.support.I18nLanguageCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缺失翻译记录器。
 *
 * <p>请求线程只做内存累加，定时任务每 10 秒批量落库，
 * 避免一个坏 key 被打爆时每次请求都写数据库。</p>
 *
 * <p>只在“数据库和资源包都没命中”时记录——资源包能兜住的键不算缺失。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nMissingTracker {

    private final I18nMissingLogMapper missingLogMapper;

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public void record(String languageCode, String keyCode, String localeTag) {
        if (languageCode == null || keyCode == null || keyCode.isEmpty()) {
            return;
        }
        String namespace = I18nLanguageCodes.namespaceOf(keyCode);
        String cacheKey = languageCode + '|' + namespace + '|' + keyCode;
        pending.compute(cacheKey, (key, old) -> {
            LocalDateTime now = LocalDateTime.now();
            if (old == null) {
                return new Pending(languageCode, namespace, keyCode, 1L, now, now, localeTag);
            }
            old.hitCount++;
            old.lastSeen = now;
            if (old.sampleLocale == null) {
                old.sampleLocale = localeTag;
            }
            return old;
        });
    }

    /** 后台“清空缺失记录”时顺带清掉未落库的内存计数。 */
    public void clearPending() {
        pending.clear();
    }

    @Scheduled(fixedDelay = 10000L, initialDelay = 15000L)
    public void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, Pending>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Pending> entry = iterator.next();
            Pending item = entry.getValue();
            try {
                I18nMissingLog row = new I18nMissingLog();
                row.setLogId(IdWorker.getId());
                row.setTenantId(0L);
                row.setLanguageCode(item.languageCode);
                row.setNamespace(item.namespace);
                row.setKeyCode(item.keyCode);
                row.setHitCount(item.hitCount);
                row.setFirstSeen(item.firstSeen);
                row.setLastSeen(item.lastSeen);
                row.setSampleLocale(item.sampleLocale);
                missingLogMapper.upsert(row);
                pending.remove(entry.getKey(), item);
            } catch (Exception e) {
                // 数据库短暂不可用时保留计数，下一轮再试，不影响接口
                log.warn("缺失翻译落库失败，下轮重试: key={}, error={}", item.keyCode, e.getMessage());
            }
        }
    }

    private static final class Pending {

        private final String languageCode;

        private final String namespace;

        private final String keyCode;

        private long hitCount;

        private final LocalDateTime firstSeen;

        private LocalDateTime lastSeen;

        private String sampleLocale;

        private Pending(String languageCode, String namespace, String keyCode, long hitCount,
                        LocalDateTime firstSeen, LocalDateTime lastSeen, String sampleLocale) {
            this.languageCode = languageCode;
            this.namespace = namespace;
            this.keyCode = keyCode;
            this.hitCount = hitCount;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.sampleLocale = sampleLocale;
        }
    }
}
