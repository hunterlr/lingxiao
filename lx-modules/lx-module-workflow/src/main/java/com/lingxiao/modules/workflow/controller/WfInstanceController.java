package com.lingxiao.modules.workflow.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.workflow.dto.InstanceDetailVO;
import com.lingxiao.modules.workflow.dto.InstanceVO;
import com.lingxiao.modules.workflow.dto.StartProcessRequest;
import com.lingxiao.modules.workflow.service.WfRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 后台工作流 — 流程实例（{@code /api/admin/workflow/instance/**}）。
 */
@RestController
@RequestMapping("/api/admin/workflow/instance")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class WfInstanceController {

    private final WfRuntimeService runtimeService;

    @RequiresPermission("platform:workflow:instance:start")
    @Log(title = "发起流程", businessType = BusinessType.INSERT)
    @PostMapping("/start")
    public R<String> start(@Valid @RequestBody StartProcessRequest request) {
        Long userId = LoginHelper.getUserId();
        String name = LoginHelper.getNickname();
        return R.ok(runtimeService.start(request, userId, name == null ? LoginHelper.getUsername() : name));
    }

    @RequiresPermission("platform:workflow:instance:mine")
    @GetMapping("/mine")
    public R<PageResult<InstanceVO>> mine(@RequestParam(defaultValue = "1") long pageNum,
                                          @RequestParam(defaultValue = "10") long pageSize,
                                          @RequestParam(required = false) String keyword) {
        return R.ok(runtimeService.mine(LoginHelper.getUserId(), keyword, pageNum, pageSize));
    }

    @RequiresPermission("platform:workflow:instance:mine")
    @GetMapping("/{processInstanceId}")
    public R<InstanceDetailVO> detail(@PathVariable String processInstanceId) {
        return R.ok(runtimeService.detail(processInstanceId));
    }

    @RequiresPermission("platform:workflow:instance:withdraw")
    @Log(title = "撤回流程", businessType = BusinessType.UPDATE)
    @PutMapping("/{processInstanceId}/withdraw")
    public R<Void> withdraw(@PathVariable String processInstanceId) {
        runtimeService.withdraw(processInstanceId, LoginHelper.getUserId());
        return R.ok();
    }
}