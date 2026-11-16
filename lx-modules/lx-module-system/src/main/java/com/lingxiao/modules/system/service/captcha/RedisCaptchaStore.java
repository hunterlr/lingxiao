package com.lingxiao.modules.system.service.captcha;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 验证码 Redis 存储。
 *
 * <p>触发条件：{@code lx.redis.enabled=true}。用于多实例部署：
 * 用户在 A 节点拿验证码、请求落到 B 节点也能校验通过。</p>
 *
 * <p>Key 统一加 {@code lx:captcha:} 前缀，避免和 Sa-Token 的
 * {@code satoken:*} 以及业务缓存混在一起；用 {@code redis-cli keys 'lx:captcha:*'}
 * 可以直接排查。</p>
 *
 * <p>用 {@link StringRedisTemplate} 而不是 JSON 序列化：验证码就是 4 位字符串，
 * 存纯文本更直观，也不涉及反序列化安全问题。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.redis.enabled", havingValue = "true")
public class RedisCaptchaStore implements CaptchaStore {

    private static final String KEY_PREFIX = "lx:captcha:";

    /** SCAN 最多统计的 Key 数量，防止极端情况下监控接口长时间占用连接 */
    private static final long SCAN_LIMIT = 10_000L;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void put(String key, String code, long ttlSeconds) {
        redisTemplate.opsForValue().set(KEY_PREFIX + key, code, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + key);
    }

    @Override
    public void remove(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }

    @Override
    public String mode() {
        return "Redis";
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("mode", mode());
        stats.put("keyPrefix", KEY_PREFIX);
        long size = 0;
        try {
            Long counted = redisTemplate.execute((RedisCallback<Long>) connection -> countKeys(connection));
            size = counted == null ? 0 : counted;
        } catch (Exception e) {
            stats.put("error", "统计失败: " + e.getMessage());
        }
        stats.put("size", size);
        if (size >= SCAN_LIMIT) {
            stats.put("note", "仅统计前 " + SCAN_LIMIT + " 个 Key");
        }
        return stats;
    }

    /**
     * 用 SCAN 而不是 KEYS 统计前缀 Key 数量。
     *
     * <p>KEYS 会阻塞 Redis 单线程，监控页是人工点击触发，但也不该在生产上冒险；
     * 这里限制最多数 10000 个，超过只报“至少这么多”，足够判断验证码缓存是否异常膨胀。</p>
     */
    private long countKeys(RedisConnection connection) {
        long count = 0;
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build();
        try (Cursor<byte[]> cursor = connection.scan(options)) {
            while (cursor.hasNext() && count < SCAN_LIMIT) {
                cursor.next();
                count++;
            }
        }
        return count;
    }
}
