package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.entity.SysDictData;
import com.lingxiao.modules.system.entity.SysDictType;
import com.lingxiao.modules.system.service.SysDictService;
import lombok.RequiredArgsConstructor;
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
 * 字典管理接口。
 *
 * <p>类型与数据分两组路径：{@code /type/**} 与 {@code /data/**}。
 * 数据查询强制带 {@code dictType}，不给“全量字典”入口。</p>
 *
 * <p>下拉数据接口 {@code /data/options?dictType=xxx} 是前端最高频的调用，
 * 权限码沿用 {@code platform:system:dict:list}，不另开“公开字典”口子。</p>
 */
@RestController
@RequestMapping("/api/admin/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;

    // ==================== 字典类型 ====================

    @RequiresPermission("platform:system:dict:list")
    @GetMapping("/type/list")
    public R<PageResult<SysDictType>> typeList(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "20") long pageSize,
                                               @RequestParam(required = false) String dictName,
                                               @RequestParam(required = false) String dictType,
                                               @RequestParam(required = false) String status) {
        return R.ok(dictService.pageType(pageNum, pageSize, dictName, dictType, status));
    }

    @RequiresPermission("platform:system:dict:list")
    @GetMapping("/type/options")
    public R<List<SysDictType>> typeOptions() {
        return R.ok(dictService.optionsType());
    }

    @RequiresPermission("platform:system:dict:list")
    @GetMapping("/type/{dictId}")
    public R<SysDictType> typeDetail(@PathVariable Long dictId) {
        return R.ok(dictService.detailType(dictId));
    }

    @RequiresPermission("platform:system:dict:add")
    @Log(title = "字典类型", businessType = BusinessType.INSERT)
    @PostMapping("/type")
    public R<Long> addType(@Valid @RequestBody SysDictType type) {
        return R.ok(dictService.createType(type));
    }

    @RequiresPermission("platform:system:dict:edit")
    @Log(title = "字典类型", businessType = BusinessType.UPDATE)
    @PutMapping("/type/{dictId}")
    public R<Void> editType(@PathVariable Long dictId, @Valid @RequestBody SysDictType type) {
        dictService.updateType(dictId, type);
        return R.ok();
    }

    @RequiresPermission("platform:system:dict:remove")
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/type/{dictId}")
    public R<Void> removeType(@PathVariable Long dictId) {
        dictService.deleteType(dictId);
        return R.ok();
    }

    // ==================== 字典数据 ====================

    @RequiresPermission("platform:system:dict:list")
    @GetMapping("/data/list")
    public R<PageResult<SysDictData>> dataList(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "20") long pageSize,
                                               @RequestParam(required = false) String dictType,
                                               @RequestParam(required = false) String dictLabel,
                                               @RequestParam(required = false) String status) {
        return R.ok(dictService.pageData(pageNum, pageSize, dictType, dictLabel, status));
    }

    /** 前端下拉框：按类型取全部启用数据。 */
    @RequiresPermission("platform:system:dict:list")
    @GetMapping("/data/options")
    public R<List<SysDictData>> dataOptions(@RequestParam String dictType) {
        return R.ok(dictService.listDataByType(dictType));
    }

    @RequiresPermission("platform:system:dict:list")
    @GetMapping("/data/{dictCode}")
    public R<SysDictData> dataDetail(@PathVariable Long dictCode) {
        return R.ok(dictService.detailData(dictCode));
    }

    @RequiresPermission("platform:system:dict:add")
    @Log(title = "字典数据", businessType = BusinessType.INSERT)
    @PostMapping("/data")
    public R<Long> addData(@Valid @RequestBody SysDictData data) {
        return R.ok(dictService.createData(data));
    }

    @RequiresPermission("platform:system:dict:edit")
    @Log(title = "字典数据", businessType = BusinessType.UPDATE)
    @PutMapping("/data/{dictCode}")
    public R<Void> editData(@PathVariable Long dictCode, @Valid @RequestBody SysDictData data) {
        dictService.updateData(dictCode, data);
        return R.ok();
    }

    @RequiresPermission("platform:system:dict:remove")
    @Log(title = "字典数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/data/{dictCode}")
    public R<Void> removeData(@PathVariable Long dictCode) {
        dictService.deleteData(dictCode);
        return R.ok();
    }
}
