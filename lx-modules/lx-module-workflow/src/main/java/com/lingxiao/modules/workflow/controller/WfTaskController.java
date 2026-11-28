package com.lingxiao.modules.workflow.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.workflow.dto.TaskActionRequest;
import com.lingxiao.modules.workflow.dto.TaskVO;
import com.lingxiao.modules.workflow.service.WfRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台工作流 — 我的待办 / 已办（{@code /api/admin/workflow/task/**}）。
 *
 * <p>“我的”由 Token 里的 userId 决定，不接受前端传用户 ID，避免越权查别人的待办。</p>
 */
@RestController
@RequestMapping("/api/admin/workflow/task")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class WfTaskController {

    private final WfRuntimeService runtimeService;

    @RequiresPermission("platform:workflow:task:todo")
    @GetMapping("/todo")
    public R<PageResult<TaskVO>> todo(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "10") long pageSize,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(runtimeService.todo(LoginHelper.getUserId(), keyword, pageNum, pageSize));
    }

    @RequiresPermission("platform:workflow:task:done")
    @GetMapping("/done")
    public R<PageResult<TaskVO>> done(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "10") long pageSize,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(runtimeService.done(LoginHelper.getUserId(), keyword, pageNum, pageSize));
    }

    @RequiresPermission("platform:workflow:task:complete")
    @Log(title = "审批任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/complete")
    public R<Void> complete(@PathVariable String taskId,
                            @RequestBody(required = false) TaskActionRequest request) {
        runtimeService.complete(taskId, LoginHelper.getUserId(), request == null ? null : request.getComment());
        return R.ok();
    }

    @RequiresPermission("platform:workflow:task:reject")
    @Log(title = "审批任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/reject")
    public R<Void> reject(@PathVariable String taskId,
                          @RequestBody(required = false) TaskActionRequest request) {
        runtimeService.reject(taskId, LoginHelper.getUserId(), request == null ? null : request.getComment());
        return R.ok();
    }
}