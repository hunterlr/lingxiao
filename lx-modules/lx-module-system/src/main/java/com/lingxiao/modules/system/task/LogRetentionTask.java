package com.lingxiao.modules.system.task;

import com.lingxiao.modules.system.service.LogRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志保留清理的兜底调度。
 *
 * <p>只有在<b>定时任务模块关闭</b>时才注册：</p>
 * <ul>
 *   <li>job 模块开启（默认）：由页面上的 {@code systemLogClean} 任务驱动，
 *       运维能改 cron、能暂停、能看到每次清理条数；</li>
 *   <li>job 模块关闭：没有页面可配，这里用 {@code lx.log.cleanCron}
 *       （默认每天 03:30）保证审计表仍然不会无限增长。</li>
 * </ul>
 *
 * <p>两条路都调用同一个 {@link LogRetentionService#cleanNow()}，
 * 不会出现两套保留天数口径。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "false", matchIfMissing = true)
public class LogRetentionTask {

    private final LogRetentionService logRetentionService;

    @Scheduled(cron = "${lx.log.cleanCron:0 30 3 * * ?}")
    public void purgeExpiredLogs() {
        try {
            logRetentionService.cleanNow();
        } catch (Exception e) {
            log.warn("日志清理失败，不影响业务: {}", e.getMessage());
        }
    }
}
