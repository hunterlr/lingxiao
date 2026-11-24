package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.entity.CmsModel;
import com.lingxiao.modules.cms.entity.CmsModelField;
import com.lingxiao.modules.cms.service.CmsModelService;
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
 * CMS 内容模型与字段管理接口。
 *
 * <p>模型是站点级配置，字段设计后作用于该模型下的所有内容。</p>
 */
@RestController
@RequestMapping("/api/admin/cms/model")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsModelController {

    private final CmsModelService modelService;

    @RequiresPermission("platform:cms:model:list")
    @GetMapping("/list")
    public R<List<CmsModel>> list(@RequestParam Long siteId,
                                  @RequestParam(required = false) String status) {
        return R.ok(modelService.list(siteId, status));
    }

    @RequiresPermission("platform:cms:model:list")
    @GetMapping("/options")
    public R<List<CmsModel>> options(@RequestParam Long siteId) {
        return R.ok(modelService.options(siteId));
    }

    @RequiresPermission("platform:cms:model:list")
    @GetMapping("/{modelId}")
    public R<CmsModel> detail(@PathVariable Long modelId) {
        return R.ok(modelService.detail(modelId));
    }

    @RequiresPermission("platform:cms:model:add")
    @Log(title = "CMS 内容模型", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsModel model) {
        return R.ok(modelService.create(model));
    }

    @RequiresPermission("platform:cms:model:edit")
    @Log(title = "CMS 内容模型", businessType = BusinessType.UPDATE)
    @PutMapping("/{modelId}")
    public R<Void> edit(@PathVariable Long modelId, @Valid @RequestBody CmsModel model) {
        modelService.update(modelId, model);
        return R.ok();
    }

    @RequiresPermission("platform:cms:model:remove")
    @Log(title = "CMS 内容模型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{modelId}")
    public R<Void> remove(@PathVariable Long modelId) {
        modelService.delete(modelId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:model:list")
    @GetMapping("/{modelId}/fields")
    public R<List<CmsModelField>> fields(@PathVariable Long modelId) {
        return R.ok(modelService.fields(modelId));
    }

    @RequiresPermission("platform:cms:field:add")
    @Log(title = "CMS 模型字段", businessType = BusinessType.INSERT)
    @PostMapping("/{modelId}/field")
    public R<Long> addField(@PathVariable Long modelId,
                            @Valid @RequestBody CmsModelField field) {
        return R.ok(modelService.addField(modelId, field));
    }

    @RequiresPermission("platform:cms:field:edit")
    @Log(title = "CMS 模型字段", businessType = BusinessType.UPDATE)
    @PutMapping("/{modelId}/field/{fieldId}")
    public R<Void> editField(@PathVariable Long modelId,
                             @PathVariable Long fieldId,
                             @Valid @RequestBody CmsModelField field) {
        modelService.updateField(modelId, fieldId, field);
        return R.ok();
    }

    @RequiresPermission("platform:cms:field:remove")
    @Log(title = "CMS 模型字段", businessType = BusinessType.DELETE)
    @DeleteMapping("/{modelId}/field/{fieldId}")
    public R<Void> removeField(@PathVariable Long modelId, @PathVariable Long fieldId) {
        modelService.removeField(modelId, fieldId);
        return R.ok();
    }
}
