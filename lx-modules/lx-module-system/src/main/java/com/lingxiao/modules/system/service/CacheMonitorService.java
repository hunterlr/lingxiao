package com.lingxiao.modules.system.service;

import com.lingxiao.common.core.i18n.MessageUtils;
import com.lingxiao.modules.system.dto.CacheInfoVO;
import com.lingxiao.modules.system.service.captcha.CaptchaStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 缓存监控（W4 系统监控）。
 *
 * <p><b>监控对象是“缓存”，不是“业务数据”：</b>v1.0 真正使用缓存的只有两处——
 * Sa-Token 会话和登录验证码。本页因此展示：当前验证码存储的实现、本机
 * Caffeine 的条目 / 命中率；启用 Redis 时再补充 Redis INFO 摘要。</p>
 *
 * <p><b>降级优先：</b>Redis 不可用、INFO 解析失败、命令统计缺失，都只写进
 * {@code warnings}，接口继续返回 200。运维打开监控页时往往正是系统不正常的时候，
 * 监控页再抛 500 只会让排查更难。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheMonitorService {

    private final CaptchaStore captchaStore;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /** 与 SaTokenRedisConfig 使用同一个开关，避免“页面说有 Redis、实际没连” */
    @Value("${lx.redis.enabled:false}")
    private boolean redisEnabled;

    public CacheInfoVO sample() {
        CacheInfoVO vo = new CacheInfoVO();
        vo.setSampleTime(LocalDateTime.now());
        vo.setRedisEnabled(redisEnabled);
        vo.setStoreMode(captchaStore.mode());
        vo.setCaptcha(safeCaptchaStats());

        if (!redisEnabled) {
            vo.setNote(MessageUtils.message("monitor.cache.note.local",
                    "当前未启用 Redis：验证码与会话保存在本机内存，以下为 Caffeine 统计。"
                            + "多实例部署请把 lx.redis.enabled 改为 true。"));
            return vo;
        }

        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            vo.setNote(MessageUtils.message("monitor.cache.note.redisNoTemplate",
                    "已启用 Redis，但容器里没有 StringRedisTemplate，已降级展示验证码存储统计。"));
            vo.getWarnings().add(MessageUtils.message("monitor.cache.warning.noTemplate",
                    "未找到 StringRedisTemplate，请检查 spring-boot-starter-data-redis 依赖"));
            return vo;
        }

        try {
            vo.setRedis(readRedis(template));
            vo.setNote(MessageUtils.message("monitor.cache.note.redis",
                    "已启用 Redis：以下为 INFO 摘要与验证码 Key 统计。"));
        } catch (Exception e) {
            // 连接超时 / 权限不足 / 代理异常：只降级，不 500
            log.warn("读取 Redis INFO 失败: {}", e.getMessage());
            vo.setNote(MessageUtils.message("monitor.cache.note.redisFailed",
                    "已启用 Redis，但本次读取失败，已降级展示验证码存储统计。"));
            vo.getWarnings().add(MessageUtils.message("monitor.cache.warning.redisError",
                    new Object[]{e.getMessage()}, "Redis 读取失败: " + e.getMessage()));
        }
        return vo;
    }

    private Map<String, Object> safeCaptchaStats() {
        try {
            return captchaStore.stats();
        } catch (Exception e) {
            log.warn("读取验证码缓存统计失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private CacheInfoVO.RedisInfo readRedis(StringRedisTemplate template) {
        Properties info = template.execute((RedisCallback<Properties>) connection ->
                connection.serverCommands().info());
        if (info == null) {
            throw new IllegalStateException("Redis INFO 返回空");
        }

        CacheInfoVO.RedisInfo redis = new CacheInfoVO.RedisInfo();
        redis.setVersion(info.getProperty("redis_version"));
        redis.setMode(info.getProperty("redis_mode"));
        redis.setPort(info.getProperty("tcp_port"));
        redis.setOs(info.getProperty("os"));
        redis.setConnectedClients(toInt(info.getProperty("connected_clients")));

        long uptime = toLong(info.getProperty("uptime_in_seconds"));
        redis.setUptimeSeconds(uptime);
        redis.setUptimeHuman(humanDuration(uptime));

        redis.setUsedMemory(toLong(info.getProperty("used_memory")));
        redis.setUsedMemoryHuman(info.getProperty("used_memory_human"));
        redis.setMaxMemory(toLong(info.getProperty("maxmemory")));
        redis.setMaxMemoryHuman(info.getProperty("maxmemory_human"));
        redis.setMemoryUsage(percent(redis.getUsedMemory(), redis.getMaxMemory()));

        long hits = toLong(info.getProperty("keyspace_hits"));
        long misses = toLong(info.getProperty("keyspace_misses"));
        redis.setKeyspaceHits(hits);
        redis.setKeyspaceMisses(misses);
        redis.setHitRate(percent(hits, hits + misses));
        redis.setExpiredKeys(toLong(info.getProperty("expired_keys")));
        redis.setEvictedKeys(toLong(info.getProperty("evicted_keys")));
        redis.setRejectedConnections(toLong(info.getProperty("rejected_connections")));
        redis.setTotalCommandsProcessed(toLong(info.getProperty("total_commands_processed")));
        redis.setTotalConnectionsReceived(toLong(info.getProperty("total_connections_received")));

        redis.setKeyspaces(readKeyspaces(info));
        redis.setCommandStats(readCommandStats(info));
        return redis;
    }

    /**
     * INFO 里 {@code keyspace} 区段形如 {@code db0:keys=12,expires=0,avg_ttl=0}。
     * Properties 解析后 key 是 {@code db0}，value 是逗号分隔的字段串。
     */
    private List<CacheInfoVO.Keyspace> readKeyspaces(Properties info) {
        List<CacheInfoVO.Keyspace> list = new ArrayList<>();
        for (String name : info.stringPropertyNames()) {
            if (!name.startsWith("db") || name.length() <= 2) {
                continue;
            }
            if (!Character.isDigit(name.charAt(2))) {
                continue;
            }
            Map<String, String> fields = splitFields(info.getProperty(name));
            CacheInfoVO.Keyspace keyspace = new CacheInfoVO.Keyspace();
            keyspace.setDb(name);
            keyspace.setKeys(toLong(fields.get("keys")));
            keyspace.setExpires(toLong(fields.get("expires")));
            keyspace.setAvgTtl(toLong(fields.get("avg_ttl")));
            list.add(keyspace);
        }
        list.sort(Comparator.comparing(CacheInfoVO.Keyspace::getDb));
        return list;
    }

    /**
     * INFO 里 {@code commandstats} 区段形如
     * {@code cmdstat_get:calls=10,usec=20,usec_per_call=2.00}。
     * Properties 会在第一个 {@code =} 处切开，所以 key 可能带 {@code :calls}，
     * 这里拼回原始行再解析，不能假设 key 就是命令名。
     */
    private List<CacheInfoVO.CommandStat> readCommandStats(Properties info) {
        List<CacheInfoVO.CommandStat> list = new ArrayList<>();
        for (String name : info.stringPropertyNames()) {
            if (!name.startsWith("cmdstat_")) {
                continue;
            }
            String raw = name + "=" + info.getProperty(name);
            int colon = raw.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String command = raw.substring("cmdstat_".length(), colon);
            Map<String, String> fields = splitFields(raw.substring(colon + 1));
            CacheInfoVO.CommandStat stat = new CacheInfoVO.CommandStat();
            stat.setCommand(command);
            stat.setCalls(toLong(fields.get("calls")));
            stat.setUsec(toLong(fields.get("usec")));
            stat.setUsecPerCall(toDouble(fields.get("usec_per_call")));
            list.add(stat);
        }
        list.sort(Comparator.comparingLong(CacheInfoVO.CommandStat::getCalls).reversed());
        return list;
    }

    private Map<String, String> splitFields(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return fields;
        }
        for (String part : text.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                fields.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return fields;
    }

    private String humanDuration(long seconds) {
        if (seconds <= 0) {
            return MessageUtils.message("monitor.cache.duration.zero", "0 秒");
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(MessageUtils.message("monitor.cache.duration.days", new Object[]{days}, days + " 天")).append(' ');
        }
        if (hours > 0 || days > 0) {
            sb.append(MessageUtils.message("monitor.cache.duration.hours", new Object[]{hours}, hours + " 小时")).append(' ');
        }
        sb.append(MessageUtils.message("monitor.cache.duration.minutes", new Object[]{minutes}, minutes + " 分"));
        return sb.toString().trim();
    }

    private double percent(long used, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.round(used * 10000.0 / total) / 100.0;
    }

    private long toLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private int toInt(String value) {
        return (int) toLong(value);
    }

    private double toDouble(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
