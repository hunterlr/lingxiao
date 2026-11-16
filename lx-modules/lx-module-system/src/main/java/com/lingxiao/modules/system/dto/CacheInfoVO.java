package com.lingxiao.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 缓存监控快照。
 *
 * <p><b>两种部署形态都要能看：</b></p>
 * <ul>
 *   <li>{@code lx.redis.enabled=false}：验证码与 Sa-Token 都在本机内存，
 *       展示 Caffeine 的条目数 / 命中率 / 淘汰数，并明确提示“未启用 Redis”；</li>
 *   <li>{@code lx.redis.enabled=true}：读 Redis {@code INFO} 的 server / memory /
 *       stats / keyspace / commandstats 区段，展示版本、内存、命中率、库键数。</li>
 * </ul>
 *
 * <p>Redis 连接异常只降级：把异常信息放进 {@code warnings}，接口仍返回 200。
 * 监控页本身不该成为新的故障点——运维往往正是在 Redis 出问题时才打开它。</p>
 */
@Data
public class CacheInfoVO {

    private LocalDateTime sampleTime;

    /** 是否启用了 Redis（对应 lx.redis.enabled） */
    private boolean redisEnabled;

    /** 验证码存储实现名：Caffeine / Redis */
    private String storeMode;

    /** 页面顶部的一句话说明，例如“当前未启用 Redis，以下为本机 Caffeine 统计” */
    private String note;

    private List<String> warnings = new ArrayList<>();

    /** 验证码存储统计（字段由 CaptchaStore 实现约定） */
    private Map<String, Object> captcha;

    /** Redis 指标；未启用或连接失败时为 null */
    private RedisInfo redis;

    /** Redis INFO 里摘出来的常用指标 */
    @Data
    public static class RedisInfo {
        private String version;
        private String mode;
        private String port;
        private String os;
        private long uptimeSeconds;
        private String uptimeHuman;
        private int connectedClients;

        private long usedMemory;
        private String usedMemoryHuman;
        private long maxMemory;
        private String maxMemoryHuman;
        private double memoryUsage;

        private long keyspaceHits;
        private long keyspaceMisses;
        private double hitRate;
        private long expiredKeys;
        private long evictedKeys;
        private long rejectedConnections;
        private long totalCommandsProcessed;
        private long totalConnectionsReceived;

        private List<Keyspace> keyspaces = new ArrayList<>();
        private List<CommandStat> commandStats = new ArrayList<>();
    }

    /** 单个逻辑库的键统计（对应 INFO keyspace 的 db0 / db1 ...） */
    @Data
    public static class Keyspace {
        private String db;
        private long keys;
        private long expires;
        private long avgTtl;
    }

    /** 单条命令调用统计（对应 INFO commandstats 的 cmdstat_xxx） */
    @Data
    public static class CommandStat {
        private String command;
        private long calls;
        private long usec;
        private double usecPerCall;
    }
}
