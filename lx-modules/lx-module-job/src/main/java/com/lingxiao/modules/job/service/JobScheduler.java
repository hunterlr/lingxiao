package com.lingxiao.modules.job.service;

import com.lingxiao.modules.job.entity.SysJob;
import com.lingxiao.modules.job.mapper.SysJobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 任务调度器。
 *
 * <p>为每个“正常”状态的任务向独立的 {@link ThreadPoolTaskScheduler} 注册一个
 * {@link CronTrigger}。没有用 Spring 默认的 {@code @Scheduled} 调度线程，
 * 原因是任务页要能随时增删改 cron，而 {@code @Scheduled} 的表达式在启动时就固定了。</p>
 *
 * <p>启动时机放在 {@link ApplicationReadyEvent}，而不是 {@code @PostConstruct}：
 * 此时 Flyway 已经迁移完、任务表可读，避免“表还没建就查任务”导致启动失败。</p>
 *
 * <p>执行线程池与系统模块的 {@code LogRetentionTask} 默认调度器相互独立：
 * 一个长任务不会拖住其它 {@code @Scheduled} 任务。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class JobScheduler implements ApplicationListener<ApplicationReadyEvent> {

    private static final String STATUS_NORMAL = "0";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysJobMapper jobMapper;
    private final JobExecutor jobExecutor;
    private final int poolSize;
    private final int shutdownWaitSeconds;

    private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private ThreadPoolTaskScheduler scheduler;

    public JobScheduler(SysJobMapper jobMapper,
                        JobExecutor jobExecutor,
                        @Value("${lx.job.pool-size:4}") int poolSize,
                        @Value("${lx.job.shutdown-wait-seconds:10}") int shutdownWaitSeconds) {
        this.jobMapper = jobMapper;
        this.jobExecutor = jobExecutor;
        this.poolSize = Math.max(poolSize, 1);
        this.shutdownWaitSeconds = Math.max(shutdownWaitSeconds, 0);
    }

    @PostConstruct
    public void init() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("lx-job-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(shutdownWaitSeconds);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        reload();
    }

    /**
     * 全量重载：先取消本机所有调度，再按库里“正常”状态的任务重新注册。
     *
     * <p>只在启动时调用；单任务变更走 {@link #refresh(SysJob)}。</p>
     */
    public synchronized void reload() {
        futures.values().forEach(future -> future.cancel(false));
        futures.clear();
        List<SysJob> jobs;
        try {
            jobs = jobMapper.selectEnabled();
        } catch (Exception e) {
            log.error("加载定时任务失败，本次不注册任何任务: {}", e.getMessage(), e);
            return;
        }
        for (SysJob job : jobs) {
            schedule(job);
        }
        log.info("定时任务已加载 {} 个（正常状态）", futures.size());
    }

    /**
     * 单个任务刷新：cron / 状态 / 执行器变化后调用。
     *
     * <p>先取消旧调度再判断状态，所以“暂停”也会走这里。</p>
     */
    public synchronized void refresh(SysJob job) {
        if (job == null || job.getJobId() == null) {
            return;
        }
        cancel(job.getJobId());
        if (STATUS_NORMAL.equals(job.getStatus())) {
            schedule(job);
        }
    }

    public synchronized void cancel(Long jobId) {
        ScheduledFuture<?> future = futures.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** 页面“执行一次”：不改变调度计划，提交到同一线程池异步执行。 */
    public void runNow(Long jobId) {
        scheduler.execute(() -> jobExecutor.executeManually(jobId));
    }

    /**
     * 计算下次执行时间，用于列表展示。
     *
     * <p>非法 cron 返回 {@code null}，不抛异常：列表不能因为一条脏数据整页报错。</p>
     */
    public String nextExecutionTime(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return null;
        }
        try {
            LocalDateTime next = CronExpression.parse(cronExpression).next(LocalDateTime.now());
            return next == null ? null : FORMATTER.format(next);
        } catch (Exception e) {
            return null;
        }
    }

    private void schedule(SysJob job) {
        try {
            CronTrigger trigger = new CronTrigger(job.getCronExpression());
            Long jobId = job.getJobId();
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> jobExecutor.executeByCron(jobId), trigger);
            if (future != null) {
                futures.put(jobId, future);
                log.info("已注册任务「{}」cron={}", job.getJobName(), job.getCronExpression());
            }
        } catch (Exception e) {
            // 单条脏 cron 不能影响其它任务；任务页会显示“下次执行 -”
            log.error("注册任务「{}」失败，cron={}，原因：{}",
                    job.getJobName(), job.getCronExpression(), e.getMessage());
        }
    }
}
