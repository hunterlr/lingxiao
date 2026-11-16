package com.lingxiao.modules.system.service.captcha;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 验证码本地存储（默认实现）。
 *
 * <p>触发条件：{@code lx.redis.enabled=false} 或未配置。单机部署够用，
 * 进程重启后验证码全部失效——这是预期行为，用户刷新一下即可。</p>
 *
 * <p>容量上限 10000：验证码接口是匿名可调的，必须防止被刷爆内存。
 * 超过上限时 Caffeine 按 Window TinyLFU 淘汰旧条目。</p>
 *
 * <p>过期时间固定 5 分钟（与 {@code CaptchaService} 的 {@code EXPIRES_IN} 对齐）；
 * 接口上的 {@code ttlSeconds} 在本地实现里不生效，换 Redis 后按条目精确过期。</p>
 */
@Component
@ConditionalOnProperty(name = "lx.redis.enabled", havingValue = "false", matchIfMissing = true)
public class CaffeineCaptchaStore implements CaptchaStore {

    private static final int MAX_CACHE_SIZE = 10_000;
    private static final long EXPIRE_SECONDS = 300;

    private final Cache<String, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(EXPIRE_SECONDS, TimeUnit.SECONDS)
            .maximumSize(MAX_CACHE_SIZE)
            // 缓存监控页要展示命中率；recordStats 有少量开销，验证码是低频接口，可以接受
            .recordStats()
            .build();

    @Override
    public void put(String key, String code, long ttlSeconds) {
        cache.put(key, code);
    }

    @Override
    public String get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void remove(String key) {
        cache.invalidate(key);
    }

    @Override
    public String mode() {
        return "Caffeine";
    }

    @Override
    public Map<String, Object> stats() {
        CacheStats snapshot = cache.stats();
        long requests = snapshot.requestCount();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("mode", mode());
        stats.put("size", cache.estimatedSize());
        stats.put("maxSize", MAX_CACHE_SIZE);
        stats.put("expireSeconds", EXPIRE_SECONDS);
        stats.put("hitCount", snapshot.hitCount());
        stats.put("missCount", snapshot.missCount());
        // Caffeine 在“零请求”时 hitRate() 返回 1.0，直接展示会误导；
        // 没有请求就是 0，与 Redis 的 keyspace_hits/misses 口径保持一致
        stats.put("hitRate", requests == 0 ? 0.0 : snapshot.hitRate());
        stats.put("evictionCount", snapshot.evictionCount());
        stats.put("loadCount", snapshot.loadCount());
        return stats;
    }
}
