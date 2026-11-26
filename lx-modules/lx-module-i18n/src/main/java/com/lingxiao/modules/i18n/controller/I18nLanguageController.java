package com.lingxiao.modules.i18n.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.i18n.dto.LanguageRequest;
import com.lingxiao.modules.i18n.entity.I18nLanguage;
import com.lingxiao.modules.i18n.service.I18nLanguageService;
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
 * 后台多语言 — 语言管理（{@code /api/admin/i18n/language/**}）。
 *
 * <p>模块关闭时整个 Controller 不装配，接口 404；菜单也不下发。</p>
 */
@RestController
@RequestMapping("/api/admin/i18n/language")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nLanguageController {

    private final I18nLanguageService languageService;

    @RequiresPermission("platform:i18n:language:list")
    @GetMapping("/list")
    public R<List<I18nLanguage>> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String status) {
        return R.ok(languageService.list(keyword, status));
    }

    @RequiresPermission("platform:i18n:language:list")
    @GetMapping("/options")
    public R<List<I18nLanguage>> options() {
        return R.ok(languageService.options());
    }

    @RequiresPermission("platform:i18n:language:add")
    @Log(title = "语言管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody LanguageRequest request) {
        return R.ok(languageService.create(request));
    }

    @RequiresPermission("platform:i18n:language:edit")
    @Log(title = "语言管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{languageId}")
    public R<Void> edit(@PathVariable Long languageId,
                        @Valid @RequestBody LanguageRequest request) {
        languageService.update(languageId, request);
        return R.ok();
    }

    @RequiresPermission("platform:i18n:language:remove")
    @Log(title = "语言管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{languageId}")
    public R<Void> remove(@PathVariable Long languageId) {
        languageService.delete(languageId);
        return R.ok();
    }

    @RequiresPermission("platform:i18n:language:status")
    @Log(title = "语言管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{languageId}/status")
    public R<Void> changeStatus(@PathVariable Long languageId, @RequestParam String status) {
        languageService.changeStatus(languageId, status);
        return R.ok();
    }
}
