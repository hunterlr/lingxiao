package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.entity.SysConfig;
import com.lingxiao.modules.system.service.SysConfigService;
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

/**
 * 系统参数接口。
 *
 * <p>内置参数（{@code configType='Y'}）可以改值，但服务端会拒绝改键名与删除。
 * 前端应当把这两项置灰，但真正的约束在后端。</p>
 */
@RestController
@RequestMapping("/api/admin/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

    @RequiresPermission("platform:system:config:list")
    @GetMapping("/list")
    public R<PageResult<SysConfig>> list(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "20") long pageSize,
                                         @RequestParam(required = false) String configName,
                                         @RequestParam(required = false) String configKey,
                                         @RequestParam(required = false) String configType,
                                         @RequestParam(required = false) String controlType) {
        return R.ok(configService.page(pageNum, pageSize, configName, configKey, configType, controlType));
    }

    @RequiresPermission("platform:system:config:list")
    @GetMapping("/{configId}")
    public R<SysConfig> detail(@PathVariable Long configId) {
        return R.ok(configService.detail(configId));
    }

    /** 按键名取值（前端可用来读开关类参数）。 */
    @RequiresPermission("platform:system:config:list")
    @GetMapping("/value")
    public R<String> value(@RequestParam String configKey) {
        return R.ok(configService.getValueByKey(configKey));
    }

    /**
     * 读取公开参数：登录即可，不需要 {@code config:list} 权限。
     *
     * <p>服务端白名单控制可读 key（当前只有 {@code sys.editor.type}）；
     * 非白名单 key 返回 null，不区分“没权限”和“不存在”。</p>
     */
    @GetMapping("/public/value")
    public R<String> publicValue(@RequestParam String configKey) {
        return R.ok(configService.getPublicValue(configKey));
    }

    @RequiresPermission("platform:system:config:add")
    @Log(title = "系统参数", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysConfig config) {
        return R.ok(configService.create(config));
    }

    @RequiresPermission("platform:system:config:edit")
    @Log(title = "系统参数", businessType = BusinessType.UPDATE)
    @PutMapping("/{configId}")
    public R<Void> edit(@PathVariable Long configId, @Valid @RequestBody SysConfig config) {
        configService.update(configId, config);
        return R.ok();
    }

    @RequiresPermission("platform:system:config:remove")
    @Log(title = "系统参数", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configId}")
    public R<Void> remove(@PathVariable Long configId) {
        configService.delete(configId);
        return R.ok();
    }
}
