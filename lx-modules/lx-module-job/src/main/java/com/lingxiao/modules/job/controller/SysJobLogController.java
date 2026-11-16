package com.lingxiao.modules.job.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.job.entity.SysJobLog;
import com.lingxiao.modules.job.service.SysJobLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 定时任务日志接口。
 *
 * <p>日志是排查“任务到底跑没跑、为什么失败”的唯一依据，因此单独一个
 * {@code platform:monitor:job:log} 权限；清空是危险操作，再拆一个
 * {@code platform:monitor:job:log:clean}。</p>
 */
@RestController
@RequestMapping("/api/admin/monitor/job/log")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class SysJobLogController {

    private final SysJobLogService jobLogService;

    @RequiresPermission("platform:monitor:job:log")
    @GetMapping("/list")
    public R<PageResult<SysJobLog>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(jobLogService.page(pageNum, pageSize, jobId, jobName, status, beginTime, endTime));
    }

    @RequiresPermission("platform:monitor:job:log:clean")
    @Log(title = "定时任务日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public R<Integer> clean() {
        return R.ok(jobLogService.clean());
    }
}
