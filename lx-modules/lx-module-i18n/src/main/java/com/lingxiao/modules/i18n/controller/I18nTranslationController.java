package com.lingxiao.modules.i18n.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.i18n.dto.CoverageVO;
import com.lingxiao.modules.i18n.dto.ImportResultVO;
import com.lingxiao.modules.i18n.dto.KeyRequest;
import com.lingxiao.modules.i18n.dto.TranslationRowVO;
import com.lingxiao.modules.i18n.dto.ValueBatchRequest;
import com.lingxiao.modules.i18n.entity.I18nMissingLog;
import com.lingxiao.modules.i18n.service.I18nTranslationService;
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
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

/**
 * 后台多语言 — 翻译键与译文（{@code /api/admin/i18n/**}）。
 *
 * <p>译文保存只写草稿；发布在语言包页面完成，避免编辑即上线。</p>
 */
@RestController
@RequestMapping("/api/admin/i18n")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nTranslationController {

    private final I18nTranslationService translationService;

    // ==================== 键 ====================

    @RequiresPermission("platform:i18n:key:list")
    @GetMapping("/key/list")
    public R<PageResult<TranslationRowVO>> keyList(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "20") long pageSize,
                                                   @RequestParam(required = false) String namespace,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status) {
        return R.ok(translationService.pageKeys(pageNum, pageSize, namespace, keyword, status));
    }

    /** 翻译矩阵与 key 列表同源；保留独立路径，前端语义更清楚。 */
    @RequiresPermission("platform:i18n:key:list")
    @GetMapping("/value/list")
    public R<PageResult<TranslationRowVO>> valueList(@RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "20") long pageSize,
                                                     @RequestParam(required = false) String namespace,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status) {
        return R.ok(translationService.pageKeys(pageNum, pageSize, namespace, keyword, status));
    }

    @RequiresPermission("platform:i18n:key:add")
    @Log(title = "翻译键", businessType = BusinessType.INSERT)
    @PostMapping("/key")
    public R<Long> addKey(@Valid @RequestBody KeyRequest request) {
        return R.ok(translationService.createKey(request));
    }

    @RequiresPermission("platform:i18n:key:edit")
    @Log(title = "翻译键", businessType = BusinessType.UPDATE)
    @PutMapping("/key/{keyId}")
    public R<Void> editKey(@PathVariable Long keyId, @Valid @RequestBody KeyRequest request) {
        translationService.updateKey(keyId, request);
        return R.ok();
    }

    @RequiresPermission("platform:i18n:key:remove")
    @Log(title = "翻译键", businessType = BusinessType.DELETE)
    @DeleteMapping("/key/{keyId}")
    public R<Void> removeKey(@PathVariable Long keyId) {
        translationService.deleteKey(keyId);
        return R.ok();
    }

    // ==================== 译文 ====================

    @RequiresPermission("platform:i18n:value:batch")
    @Log(title = "译文维护", businessType = BusinessType.UPDATE)
    @PostMapping("/value/batch")
    public R<Void> batchSave(@Valid @RequestBody ValueBatchRequest request) {
        translationService.batchSave(request, LoginHelper.getUserId());
        return R.ok();
    }

    @RequiresPermission("platform:i18n:import")
    @Log(title = "译文导入", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public R<ImportResultVO> importCsv(@RequestParam("file") MultipartFile file) {
        return R.ok(translationService.importCsv(file));
    }

    @RequiresPermission("platform:i18n:export")
    @GetMapping("/export")
    public void exportCsv(@RequestParam(required = false) String namespace,
                          @RequestParam(required = false) List<String> languages,
                          @RequestParam(defaultValue = "false") boolean onlyMissing,
                          HttpServletResponse response) throws IOException {
        String content = translationService.exportCsv(namespace, languages, onlyMissing);
        String filename = "i18n-" + (namespace == null || namespace.isEmpty() ? "all" : namespace) + ".csv";
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getWriter().write('\uFEFF');
        response.getWriter().write(content);
        response.getWriter().flush();
    }

    // ==================== 覆盖率 / 缺失 ====================

    @RequiresPermission("platform:i18n:coverage")
    @GetMapping("/coverage")
    public R<List<CoverageVO>> coverage(@RequestParam String languageCode,
                                        @RequestParam(required = false) String namespace) {
        return R.ok(translationService.coverage(languageCode, namespace));
    }

    @RequiresPermission("platform:i18n:missing")
    @GetMapping("/missing")
    public R<PageResult<I18nMissingLog>> missing(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "20") long pageSize,
                                                 @RequestParam(required = false) String languageCode,
                                                 @RequestParam(required = false) String namespace) {
        return R.ok(translationService.pageMissing(pageNum, pageSize, languageCode, namespace));
    }

    @RequiresPermission("platform:i18n:missing")
    @Log(title = "缺失翻译", businessType = BusinessType.CLEAN)
    @DeleteMapping("/missing")
    public R<Void> clearMissing(@RequestParam(required = false) String languageCode,
                                @RequestParam(required = false) String namespace) {
        translationService.clearMissing(languageCode, namespace);
        return R.ok();
    }
}
