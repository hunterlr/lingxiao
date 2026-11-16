package com.lingxiao.modules.system.task;

import com.lingxiao.common.core.job.JobHandler;
import com.lingxiao.modules.system.service.LogRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * “清理过期日志”任务的执行器。
 *
 * <p>由菜单里的演示任务 {@code job_id = 1} 使用（执行器名 {@code systemLogClean}）。
 * 放在系统模块而不是 job 模块，是为了让 job 模块保持“只负责调度、不认识任何业务表”。</p>
 *
 * <p>只有 job 模块开启时才注册；关闭时由 {@code LogRetentionTask} 兜底。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class SystemLogCleanJobHandler implements JobHandler {

    private final LogRetentionService logRetentionService;

    @Override
    public String name() {
        return "systemLogClean";
    }

    @Override
    public String description() {
        return "按 sys.log.keepDays 清理过期的操作日志与登录日志";
    }

    @Override
    public String execute(String params) {
        return logRetentionService.cleanNow();
    }
}
