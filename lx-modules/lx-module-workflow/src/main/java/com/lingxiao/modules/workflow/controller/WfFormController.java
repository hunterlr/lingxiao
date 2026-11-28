package com.lingxiao.modules.workflow.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.workflow.dto.FormRequest;
import com.lingxiao.modules.workflow.dto.FormVO;
import com.lingxiao.modules.workflow.service.WfFormService;
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

/**
 * 后台工作流 — 表单管理（{@code /api/admin/workflow/form/**}）。
 *
 * <p>编辑与发布分离：编辑只改草稿，发布才生成快照供发起使用。</p>
 */
@RestController
@RequestMapping("/api/admin/workflow/form")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class WfFormController {

    private final WfFormService formService;

    @RequiresPermission("platform:workflow:form:list")
    @GetMapping("/list")
    public R<PageResult<FormVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "10") long pageSize,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(formService.page(pageNum, pageSize, keyword));
    }

    @RequiresPermission("platform:workflow:form:list")
    @GetMapping("/{formId}")
    public R<FormVO> detail(@PathVariable Long formId) {
        return R.ok(formService.detail(formId));
    }

    @RequiresPermission("platform:workflow:form:add")
    @Log(title = "表单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody FormRequest request) {
        return R.ok(formService.save(null, request, LoginHelper.getUserId()));
    }

    @RequiresPermission("platform:workflow:form:edit")
    @Log(title = "表单管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{formId}")
    public R<Long> edit(@PathVariable Long formId, @Valid @RequestBody FormRequest request) {
        return R.ok(formService.save(formId, request, LoginHelper.getUserId()));
    }

    @RequiresPermission("platform:workflow:form:publish")
    @Log(title = "表单管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{formId}/publish")
    public R<Integer> publish(@PathVariable Long formId) {
        return R.ok(formService.publish(formId, LoginHelper.getUserId()));
    }

    @RequiresPermission("platform:workflow:form:remove")
    @Log(title = "表单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formId}")
    public R<Void> remove(@PathVariable Long formId) {
        formService.delete(formId);
        return R.ok();
    }
}