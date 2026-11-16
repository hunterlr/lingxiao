package com.lingxiao.modules.job.handler;

import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.job.JobHandler;
import com.lingxiao.modules.job.mapper.SysJobLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 清理定时任务日志。
 *
 * <p>任务日志本身也会无限增长：一个每分钟执行的任务，一天就是 1440 行。
 * 保留天数来自参数设置 {@code sys.job.logKeepDays}，页面上可改，不必重启。</p>
 *
 * <p><b>安全默认值：</b>参数缺失时用 30 天；参数非法或小于 1 时直接让任务失败
 * （页面上能看到红色失败日志），而不是退回到某个猜出来的天数——
 * 清理是不可逆操作，宁可少删。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class JobLogCleanHandler implements JobHandler {

    /** 参数设置里的键；改键名要同步改 V11 迁移 */
    public static final String CONFIG_KEEP_DAYS = "sys.job.logKeepDays";

    private static final int DEFAULT_KEEP_DAYS = 30;
    private static final int MAX_KEEP_DAYS = 3650;

    private final SysJobLogMapper jobLogMapper;
    private final ObjectProvider<ConfigProvider> configProvider;

    public JobLogCleanHandler(SysJobLogMapper jobLogMapper,
                              ObjectProvider<ConfigProvider> configProvider) {
        this.jobLogMapper = jobLogMapper;
        this.configProvider = configProvider;
    }

    @Override
    public String name() {
        return "jobLogClean";
    }

    @Override
    public String description() {
        return "清理超过 sys.job.logKeepDays 天的定时任务日志";
    }

    @Override
    public String execute(String params) {
        int keepDays = resolveKeepDays();
        LocalDateTime deadline = LocalDateTime.now().minusDays(keepDays);
        int deleted = jobLogMapper.deleteBefore(deadline);
        log.info("任务日志清理完成 | 保留天数={} | 截止={} | 删除={} 条", keepDays, deadline, deleted);
        return String.format("保留 %d 天（截止 %s），删除任务日志 %d 条", keepDays, deadline, deleted);
    }

    private int resolveKeepDays() {
        ConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            return DEFAULT_KEEP_DAYS;
        }
        int days = provider.getIntValue(CONFIG_KEEP_DAYS, DEFAULT_KEEP_DAYS);
        if (days < 1) {
            throw new IllegalStateException("参数 " + CONFIG_KEEP_DAYS + " 非法（" + days + "），已跳过清理");
        }
        return Math.min(days, MAX_KEEP_DAYS);
    }
}
