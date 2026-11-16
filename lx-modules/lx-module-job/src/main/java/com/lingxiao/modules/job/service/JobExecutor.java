package com.lingxiao.modules.job.service;

import com.lingxiao.common.core.job.JobHandler;
import com.lingxiao.modules.job.entity.SysJob;
import com.lingxiao.modules.job.entity.SysJobLog;
import com.lingxiao.modules.job.mapper.SysJobLogMapper;
import com.lingxiao.modules.job.mapper.SysJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务执行器。
 *
 * <p>一次执行的完整链路：</p>
 * <ol>
 *   <li>读任务快照（任务可能刚被删 / 刚被暂停）；</li>
 *   <li>{@code concurrent=1} 时用本机内存标记挡住重叠执行；</li>
 *   <li>向数据库抢集群锁，抢不到就跳过（另一个节点正在跑）；</li>
 *   <li>调用 {@link JobHandler#execute(String)}；</li>
 *   <li>无论成功失败都写一条 {@code sys_job_log}，最后释放锁。</li>
 * </ol>
 *
 * <p>为什么日志在 finally 之前写：成功与失败都要留痕，且要带上耗时。
 * 写日志本身失败只打 WARN，不能把“任务已经成功”改成“执行失败”。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class JobExecutor {

    /** 状态：0 成功 / 1 失败 */
    private static final String RESULT_SUCCESS = "0";
    private static final String RESULT_FAIL = "1";

    /** 并发策略：0 允许 / 1 禁止 */
    private static final String CONCURRENT_FORBID = "1";

    private static final String TRIGGER_CRON = "CRON";
    private static final String TRIGGER_MANUAL = "MANUAL";

    private static final int MESSAGE_MAX = 1000;
    private static final int STACK_MAX = 4000;

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final JobHandlerRegistry handlerRegistry;
    private final JobLockService lockService;

    /** 本机正在执行的任务：只用于 concurrent=1 的“同节点不重叠”，跨节点靠数据库锁 */
    private final Map<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    /** 调度线程调用。 */
    public void executeByCron(Long jobId) {
        execute(jobId, TRIGGER_CRON);
    }

    /** 页面“执行一次”。 */
    public void executeManually(Long jobId) {
        execute(jobId, TRIGGER_MANUAL);
    }

    private void execute(Long jobId, String triggerType) {
        SysJob job = jobMapper.selectById(jobId);
        if (job == null) {
            log.warn("任务 {} 不存在或已删除，跳过本次执行", jobId);
            return;
        }

        AtomicBoolean running = null;
        if (CONCURRENT_FORBID.equals(job.getConcurrent())) {
            running = runningFlags.computeIfAbsent(jobId, key -> new AtomicBoolean(false));
            if (!running.compareAndSet(false, true)) {
                log.warn("任务「{}」上一次还没执行完，本次跳过（并发策略=禁止）", job.getJobName());
                return;
            }
        }

        LocalDateTime start = LocalDateTime.now();
        String owner = null;
        try {
            owner = lockService.tryLock(jobId);
            if (owner == null) {
                return;
            }
            JobHandler handler = handlerRegistry.get(job.getInvokeTarget());
            if (handler == null) {
                throw new IllegalStateException("执行器不存在：" + job.getInvokeTarget());
            }
            String message = handler.execute(null);
            saveLog(job, triggerType, start, RESULT_SUCCESS,
                    message == null || message.trim().isEmpty() ? "执行成功" : message, null);
        } catch (Exception e) {
            saveLog(job, triggerType, start, RESULT_FAIL,
                    "执行失败：" + rootMessage(e), stackTrace(e));
            log.error("定时任务执行失败 | 任务={} | 执行器={}", job.getJobName(), job.getInvokeTarget(), e);
        } finally {
            lockService.unlock(jobId, owner);
            if (running != null) {
                running.set(false);
            }
        }
    }

    private void saveLog(SysJob job, String triggerType, LocalDateTime start,
                         String status, String message, String exception) {
        try {
            LocalDateTime end = LocalDateTime.now();
            SysJobLog jobLog = new SysJobLog();
            jobLog.setJobId(job.getJobId());
            jobLog.setJobName(job.getJobName());
            jobLog.setJobGroup(job.getJobGroup());
            jobLog.setInvokeTarget(job.getInvokeTarget());
            jobLog.setTriggerType(triggerType);
            jobLog.setJobMessage(truncate(message, MESSAGE_MAX));
            jobLog.setStatus(status);
            jobLog.setExceptionInfo(truncate(exception, STACK_MAX));
            jobLog.setStartTime(start);
            jobLog.setEndTime(end);
            jobLog.setDurationMs(Duration.between(start, end).toMillis());
            jobLog.setCreateTime(end);
            jobLogMapper.insert(jobLog);
        } catch (Exception e) {
            log.warn("写任务日志失败（任务本身结果不受影响）：{}", e.getMessage());
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.trim().isEmpty() ? cursor.getClass().getSimpleName() : message;
    }

    private static String stackTrace(Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
