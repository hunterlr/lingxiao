package com.lingxiao.modules.job.service;

import com.lingxiao.modules.job.mapper.SysJobLockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 集群锁。
 *
 * <p>解决的问题：多实例部署时，每个实例都会按 cron 触发同一个任务。
 * 没有锁就会重复发邮件、重复清理、重复扣款。这里用数据库行锁把
 * “同一任务同一时刻只允许一个节点执行”变成原子操作。</p>
 *
 * <p>为什么不用 Redis 分布式锁：Redis 是可选基础设施（{@code lx.redis.enabled=false}），
 * 而数据库是必有的；锁表只有一行一任务，成本可以忽略。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class JobLockService {

    private final SysJobLockMapper lockMapper;
    private final long lockTimeoutSeconds;
    private final String hostName;

    public JobLockService(SysJobLockMapper lockMapper,
                          @Value("${lx.job.lock-timeout-seconds:600}") long lockTimeoutSeconds) {
        this.lockMapper = lockMapper;
        this.lockTimeoutSeconds = Math.max(lockTimeoutSeconds, 10);
        this.hostName = resolveHostName();
    }

    /**
     * 确保锁行存在。
     *
     * <p>任务创建时调用；历史数据 / 手工插库遗漏时，执行前也会兜底补一行。
     * 并发补行由主键冲突兜住：谁先插入都行，后到的吞掉 DuplicateKeyException。</p>
     */
    public void ensureLockRow(Long jobId) {
        try {
            lockMapper.insertRow(jobId);
        } catch (DuplicateKeyException ignored) {
            // 已经有了，正常情况
        }
    }

    /**
     * 尝试抢锁。
     *
     * @return 抢到返回持有者标识（释放时要用），抢不到返回 {@code null}
     */
    public String tryLock(Long jobId) {
        ensureLockRow(jobId);
        String owner = hostName + ":" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = now.minusSeconds(lockTimeoutSeconds);
        int rows = lockMapper.tryAcquire(jobId, owner, now, expire);
        if (rows > 0) {
            return owner;
        }
        log.info("任务 {} 的集群锁被其它节点持有，本次跳过", jobId);
        return null;
    }

    /**
     * 释放锁。
     *
     * <p>条件里带 owner：如果本次执行超过超时时间、锁已被别的节点接管，
     * 这里不会把别人的锁清掉。</p>
     */
    public void unlock(Long jobId, String owner) {
        if (owner == null) {
            return;
        }
        try {
            lockMapper.release(jobId, owner);
        } catch (Exception e) {
            // 释放失败只能等超时，不能影响任务结果落库
            log.warn("释放任务 {} 的集群锁失败：{}", jobId, e.getMessage());
        }
    }

    public void remove(Long jobId) {
        try {
            lockMapper.deleteByJobId(jobId);
        } catch (Exception e) {
            log.warn("删除任务 {} 的锁行失败：{}", jobId, e.getMessage());
        }
    }

    public long getLockTimeoutSeconds() {
        return lockTimeoutSeconds;
    }

    private static String resolveHostName() {
        String name;
        try {
            name = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            name = "unknown";
        }
        if (name == null || name.isEmpty()) {
            name = "unknown";
        }
        return name.length() > 64 ? name.substring(0, 64) : name;
    }
}
