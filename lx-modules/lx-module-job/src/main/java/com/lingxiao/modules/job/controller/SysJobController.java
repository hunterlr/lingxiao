package com.lingxiao.modules.job.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.job.dto.JobHandlerVO;
import com.lingxiao.modules.job.entity.SysJob;
import com.lingxiao.modules.job.service.SysJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 定时任务接口。
 *
 * <p>路径挂在 {@code /monitor/job} 下，与菜单“系统监控 → 定时任务”一致；
 * 权限码沿用 V2 已种好的 {@code platform:monitor:job:*}。</p>
 *
 * <p>模块开关关闭时整个 Controller 不装配，接口返回 404——
 * 这正是“关闭 job 模块后不加载”的验收方式。</p>
 */
@RestController
@RequestMapping("/api/admin/monitor/job")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class SysJobController {

    private final SysJobService jobService;

    @RequiresPermission("platform:monitor:job:list")
    @GetMapping("/list")
    public R<PageResult<SysJob>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) String status) {
        return R.ok(jobService.page(pageNum, pageSize, jobName, jobGroup, status));
    }

    /** 执行器下拉；不返回执行器 Bean 名称之外的任何内部对象。 */
    @RequiresPermission("platform:monitor:job:list")
    @GetMapping("/handlers")
    public R<List<JobHandlerVO>> handlers() {
        return R.ok(jobService.handlers());
    }

    @RequiresPermission("platform:monitor:job:list")
    @GetMapping("/{jobId}")
    public R<SysJob> detail(@PathVariable Long jobId) {
        return R.ok(jobService.detail(jobId));
    }

    @RequiresPermission("platform:monitor:job:add")
    @Log(title = "定时任务", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysJob job) {
        return R.ok(jobService.create(job));
    }

    @RequiresPermission("platform:monitor:job:edit")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/{jobId}")
    public R<Void> update(@PathVariable Long jobId, @Valid @RequestBody SysJob job) {
        jobService.update(jobId, job);
        return R.ok();
    }

    @RequiresPermission("platform:monitor:job:remove")
    @Log(title = "定时任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{jobId}")
    public R<Void> remove(@PathVariable Long jobId) {
        jobService.delete(jobId);
        return R.ok();
    }

    /** 启停独立接口：只改 status，不覆盖表单其它字段。 */
    @RequiresPermission("platform:monitor:job:changeStatus")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/{jobId}/status")
    public R<Void> changeStatus(@PathVariable Long jobId, @RequestParam String status) {
        jobService.changeStatus(jobId, status);
        return R.ok();
    }

    /**
     * 立即执行一次。
     *
     * <p>异步提交：接口只负责“把任务放进线程池”，真正的执行结果去日志里看。
     * 同步等待会让一个跑 5 分钟的任务把 HTTP 连接占 5 分钟。</p>
     */
    @RequiresPermission("platform:monitor:job:run")
    @Log(title = "定时任务", businessType = BusinessType.OTHER)
    @PostMapping("/{jobId}/run")
    public R<Void> run(@PathVariable Long jobId) {
        jobService.runOnce(jobId);
        return R.ok();
    }
}
