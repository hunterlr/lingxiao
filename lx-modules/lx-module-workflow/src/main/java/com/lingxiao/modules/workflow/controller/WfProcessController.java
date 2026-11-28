package com.lingxiao.modules.workflow.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.workflow.dto.ProcessDefVO;
import com.lingxiao.modules.workflow.entity.WfCategory;
import com.lingxiao.modules.workflow.service.WfProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 后台工作流 — 流程定义（{@code /api/admin/workflow/process/**}）。
 *
 * <p>模块关闭时整个 Controller 不装配，接口 404；菜单也不下发。</p>
 */
@RestController
@RequestMapping("/api/admin/workflow/process")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class WfProcessController {

    private final WfProcessService processService;

    @RequiresPermission("platform:workflow:process:list")
    @GetMapping("/list")
    public R<PageResult<ProcessDefVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String categoryKey) {
        return R.ok(processService.page(pageNum, pageSize, keyword, categoryKey));
    }

    /** 分类下拉；第一刀只有内置的“通用”，留给后面分组用。 */
    @RequiresPermission("platform:workflow:process:list")
    @GetMapping("/categories")
    public R<List<WfCategory>> categories() {
        return R.ok(processService.categories());
    }

    @RequiresPermission("platform:workflow:process:import")
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    @PostMapping("/import")
    public R<ProcessDefVO> importBpmn(@RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) String categoryKey,
                                      @RequestParam(required = false) String remark) throws IOException {
        return R.ok(processService.importBpmn(file.getOriginalFilename(), file.getBytes(), categoryKey, remark));
    }

    @RequiresPermission("platform:workflow:process:status")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @PutMapping("/{defId}/status")
    public R<Void> changeStatus(@PathVariable Long defId, @RequestParam String status) {
        processService.changeStatus(defId, status);
        return R.ok();
    }

    @RequiresPermission("platform:workflow:process:remove")
    @Log(title = "流程定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{defId}")
    public R<Void> remove(@PathVariable Long defId) {
        processService.delete(defId);
        return R.ok();
    }
}