package com.lingxiao.modules.i18n.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.i18n.dto.CoverageRow;
import com.lingxiao.modules.i18n.dto.CoverageVO;
import com.lingxiao.modules.i18n.dto.ImportResultVO;
import com.lingxiao.modules.i18n.dto.KeyRequest;
import com.lingxiao.modules.i18n.dto.TranslationRowVO;
import com.lingxiao.modules.i18n.dto.ValueBatchRequest;
import com.lingxiao.modules.i18n.entity.I18nKey;
import com.lingxiao.modules.i18n.entity.I18nLanguage;
import com.lingxiao.modules.i18n.entity.I18nMissingLog;
import com.lingxiao.modules.i18n.entity.I18nValue;
import com.lingxiao.modules.i18n.mapper.I18nKeyMapper;
import com.lingxiao.modules.i18n.mapper.I18nMissingLogMapper;
import com.lingxiao.modules.i18n.mapper.I18nValueMapper;
import com.lingxiao.modules.i18n.message.DatabaseTranslationProvider;
import com.lingxiao.modules.i18n.message.I18nMissingTracker;
import com.lingxiao.modules.i18n.support.I18nCsvCodec;
import com.lingxiao.modules.i18n.support.I18nLanguageCodes;
import com.lingxiao.modules.i18n.support.I18nPlaceholders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 翻译键 / 译文工作台。
 *
 * <p>关键约定：</p>
 * <ul>
 *   <li>保存译文只写草稿（{@code review_status=0}），发布由语言包服务负责；</li>
 *   <li>有译文的键不物理删除，只停用，避免历史语言包 / 日志指向空行；</li>
 *   <li>CSV 导入与后台编辑走同一套占位符校验，不允许“导入绕过”。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nTranslationService {

    private static final long MAX_PAGE_SIZE = 200;

    private static final int MAX_IMPORT_ERRORS = 50;

    private final I18nKeyMapper keyMapper;

    private final I18nValueMapper valueMapper;

    private final I18nMissingLogMapper missingLogMapper;

    private final I18nLanguageService languageService;

    private final DatabaseTranslationProvider translationProvider;

    private final I18nMissingTracker missingTracker;

    // ==================== 翻译键 ====================

    public PageResult<TranslationRowVO> pageKeys(long pageNum, long pageSize, String namespace,
                                                 String keyword, String status) {
        LambdaQueryWrapper<I18nKey> wrapper = new LambdaQueryWrapper<I18nKey>()
                .eq(StringUtils.hasText(namespace), I18nKey::getNamespace, namespace)
                .eq(StringUtils.hasText(status), I18nKey::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(I18nKey::getKeyCode, keyword)
                        .or().like(I18nKey::getDefaultValue, keyword))
                .orderByAsc(I18nKey::getNamespace)
                .orderByAsc(I18nKey::getKeyCode);
        Page<I18nKey> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE));
        Page<I18nKey> result = keyMapper.selectPage(page, wrapper);
        Map<Long, Map<String, String>> values = valueMap(result.getRecords());
        List<TranslationRowVO> records = result.getRecords().stream()
                .map(item -> toRow(item, values.get(item.getKeyId())))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Transactional
    public Long createKey(KeyRequest request) {
        String keyCode = request.getKeyCode().trim();
        String namespace = StringUtils.hasText(request.getNamespace())
                ? request.getNamespace().trim() : I18nLanguageCodes.namespaceOf(keyCode);
        assertKeyFree(namespace, keyCode, null);
        I18nKey key = new I18nKey();
        key.setNamespace(namespace);
        key.setKeyCode(keyCode);
        key.setDefaultValue(request.getDefaultValue());
        key.setModuleCode(StringUtils.hasText(request.getModuleCode()) ? request.getModuleCode() : namespace);
        key.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "0");
        key.setRemark(request.getRemark());
        key.setCreateTime(LocalDateTime.now());
        keyMapper.insert(key);
        return key.getKeyId();
    }

    @Transactional
    public void updateKey(Long keyId, KeyRequest request) {
        I18nKey existing = mustGetKey(keyId);
        String keyCode = request.getKeyCode().trim();
        String namespace = StringUtils.hasText(request.getNamespace())
                ? request.getNamespace().trim() : I18nLanguageCodes.namespaceOf(keyCode);
        assertKeyFree(namespace, keyCode, keyId);
        existing.setNamespace(namespace);
        existing.setKeyCode(keyCode);
        existing.setDefaultValue(request.getDefaultValue());
        existing.setModuleCode(StringUtils.hasText(request.getModuleCode())
                ? request.getModuleCode() : namespace);
        existing.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : existing.getStatus());
        existing.setRemark(request.getRemark());
        existing.setUpdateTime(LocalDateTime.now());
        keyMapper.updateById(existing);
        translationProvider.invalidate();
    }

    @Transactional
    public void deleteKey(Long keyId) {
        I18nKey existing = mustGetKey(keyId);
        Long values = valueMapper.selectCount(new LambdaQueryWrapper<I18nValue>()
                .eq(I18nValue::getKeyId, existing.getKeyId()));
        if (values != null && values > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.key.inuse");
        }
        keyMapper.deleteById(keyId);
    }

    // ==================== 译文 ====================

    @Transactional
    public void batchSave(ValueBatchRequest request, Long operatorId) {
        for (ValueBatchRequest.ValueItem item : request.getItems()) {
            I18nKey key = mustGetKey(item.getKeyId());
            I18nLanguage language = languageService.mustGetByCode(item.getLanguageCode());
            if (!"0".equals(language.getStatus())) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.value.language.disabled");
            }
            String text = item.getValueText();
            if (StringUtils.hasText(text) && key.getDefaultValue() != null
                    && !I18nPlaceholders.sameAs(key.getDefaultValue(), text)) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.value.placeholder.mismatch");
            }
            saveValue(key.getKeyId(), language.getLanguageCode(), text, operatorId);
        }
        // 草稿只改 value_text，published_text 不变，无需失效已发布译文缓存
    }

    private void saveValue(Long keyId, String languageCode, String valueText, Long operatorId) {
        I18nValue existing = valueMapper.selectOne(new LambdaQueryWrapper<I18nValue>()
                .eq(I18nValue::getKeyId, keyId)
                .eq(I18nValue::getTenantId, 0L)
                .eq(I18nValue::getLanguageCode, languageCode));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            I18nValue value = new I18nValue();
            value.setKeyId(keyId);
            value.setTenantId(0L);
            value.setLanguageCode(languageCode);
            value.setValueText(valueText);
            value.setReviewStatus("0");
            value.setVersion(1);
            value.setTranslatedBy(operatorId);
            value.setTranslatedAt(now);
            value.setCreateTime(now);
            valueMapper.insert(value);
            return;
        }
        existing.setValueText(valueText);
        existing.setReviewStatus("0");
        existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
        existing.setTranslatedBy(operatorId);
        existing.setTranslatedAt(now);
        existing.setUpdateTime(now);
        valueMapper.updateById(existing);
    }

    // ==================== 覆盖率 ====================

    public List<CoverageVO> coverage(String languageCode, String namespace) {
        I18nLanguage language = languageService.mustGetByCode(languageCode);
        String ns = StringUtils.hasText(namespace) ? namespace.trim() : null;
        List<CoverageRow> rows = valueMapper.selectCoverage(language.getLanguageCode(), ns);
        List<CoverageVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (CoverageRow row : rows) {
            CoverageVO vo = new CoverageVO();
            vo.setLanguageCode(language.getLanguageCode());
            vo.setNamespace(row.getNamespace());
            vo.setTotalKeys(row.getTotalKeys() == null ? 0 : row.getTotalKeys());
            vo.setTranslated(row.getTranslated() == null ? 0 : row.getTranslated());
            vo.setCoverage(vo.getTotalKeys() == 0 ? 0D
                    : Math.round(vo.getTranslated() * 10000D / vo.getTotalKeys()) / 100D);
            result.add(vo);
        }
        return result;
    }

    // ==================== 缺失记录 ====================

    public PageResult<I18nMissingLog> pageMissing(long pageNum, long pageSize,
                                                  String languageCode, String namespace) {
        LambdaQueryWrapper<I18nMissingLog> wrapper = new LambdaQueryWrapper<I18nMissingLog>()
                .eq(StringUtils.hasText(languageCode), I18nMissingLog::getLanguageCode,
                        I18nLanguageCodes.normalize(languageCode))
                .eq(StringUtils.hasText(namespace), I18nMissingLog::getNamespace, namespace)
                .orderByDesc(I18nMissingLog::getLastSeen);
        Page<I18nMissingLog> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE));
        Page<I18nMissingLog> result = missingLogMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Transactional
    public void clearMissing(String languageCode, String namespace) {
        missingLogMapper.delete(new LambdaQueryWrapper<I18nMissingLog>()
                .eq(StringUtils.hasText(languageCode), I18nMissingLog::getLanguageCode,
                        I18nLanguageCodes.normalize(languageCode))
                .eq(StringUtils.hasText(namespace), I18nMissingLog::getNamespace, namespace));
        missingTracker.clearPending();
    }

    // ==================== CSV ====================

    /**
     * 导入 CSV：只写草稿，不发布。
     *
     * <p>表头第一行按名称识别，至少包含 {@code namespace} 与 {@code key}；
     * 其它列名必须是已启用语言编码。未知键会自动创建，默认文案取导入文件里的
     * zh-CN（没有则取第一门语言）。</p>
     */
    @Transactional
    public ImportResultVO importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.import.empty");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.import.format", e.getMessage());
        }
        List<List<String>> rows = I18nCsvCodec.parse(content);
        if (rows.size() < 2) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.import.empty");
        }
        List<String> header = rows.get(0).stream().map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        int namespaceIndex = header.indexOf("namespace");
        int keyIndex = header.indexOf("key");
        if (namespaceIndex < 0 || keyIndex < 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.import.format",
                    "missing namespace/key column");
        }
        int remarkIndex = header.indexOf("remark");
        Set<String> enabled = new HashSet<>(languageService.enabledCodes());
        Map<Integer, String> languageColumns = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String code = I18nLanguageCodes.normalize(header.get(i));
            if (code != null && enabled.contains(code)) {
                languageColumns.put(i, code);
            }
        }
        if (languageColumns.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.import.format", "no language column");
        }

        ImportResultVO result = new ImportResultVO();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String namespace = cell(row, namespaceIndex);
            String keyCode = cell(row, keyIndex);
            if (!StringUtils.hasText(namespace) || !StringUtils.hasText(keyCode)) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            result.setTotal(result.getTotal() + 1);
            try {
                I18nKey key = findKey(namespace.trim(), keyCode.trim());
                if (key == null) {
                    key = createKeyFromImport(namespace.trim(), keyCode.trim(), row, languageColumns);
                }
                for (Map.Entry<Integer, String> column : languageColumns.entrySet()) {
                    String text = cell(row, column.getKey());
                    if (!StringUtils.hasText(text)) {
                        continue;
                    }
                    if (key.getDefaultValue() != null && !I18nPlaceholders.sameAs(key.getDefaultValue(), text)) {
                        throw ServiceException.keyed(ErrorCode.BAD_REQUEST,
                                "i18n.import.placeholder.mismatch", r + 1);
                    }
                    saveValue(key.getKeyId(), column.getValue(), text, null);
                }
                result.setSuccess(result.getSuccess() + 1);
            } catch (ServiceException e) {
                result.setFailed(result.getFailed() + 1);
                if (result.getErrors().size() < MAX_IMPORT_ERRORS) {
                    result.getErrors().add("line " + (r + 1) + ": " + e.getMessage());
                }
            }
        }
        log.info("CSV 导入译文: total={}, success={}, skipped={}, failed={}",
                result.getTotal(), result.getSuccess(), result.getSkipped(), result.getFailed());
        return result;
    }

    private I18nKey createKeyFromImport(String namespace, String keyCode, List<String> row,
                                        Map<Integer, String> languageColumns) {
        String defaultValue = null;
        for (Map.Entry<Integer, String> column : languageColumns.entrySet()) {
            if ("zh-CN".equals(column.getValue())) {
                defaultValue = cell(row, column.getKey());
                break;
            }
        }
        if (defaultValue == null && !languageColumns.isEmpty()) {
            defaultValue = cell(row, languageColumns.keySet().iterator().next());
        }
        I18nKey key = new I18nKey();
        key.setNamespace(namespace);
        key.setKeyCode(keyCode);
        key.setDefaultValue(defaultValue);
        key.setModuleCode(namespace);
        key.setStatus("0");
        key.setRemark("imported from csv");
        key.setCreateTime(LocalDateTime.now());
        keyMapper.insert(key);
        return key;
    }

    /**
     * 导出 CSV（不含 BOM，由 Controller 写响应头时补）。
     *
     * @param languages  语言编码，空表示全部启用语言
     * @param onlyMissing 只导出所选语言中至少一门为空的键
     */
    public String exportCsv(String namespace, List<String> languages, boolean onlyMissing) {
        List<String> codes = languages == null ? Collections.emptyList()
                : languages.stream().map(I18nLanguageCodes::normalize)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        if (codes.isEmpty()) {
            codes = languageService.enabledCodes();
        }
        List<I18nKey> keys = keyMapper.selectList(new LambdaQueryWrapper<I18nKey>()
                .eq(StringUtils.hasText(namespace), I18nKey::getNamespace, namespace)
                .eq(I18nKey::getStatus, "0")
                .orderByAsc(I18nKey::getNamespace)
                .orderByAsc(I18nKey::getKeyCode));
        Map<Long, Map<String, String>> values = valueMap(keys);
        List<String> header = new ArrayList<>();
        header.add("namespace");
        header.add("key");
        header.addAll(codes);
        header.add("remark");

        StringBuilder sb = new StringBuilder();
        sb.append(I18nCsvCodec.line(header)).append('\n');
        for (I18nKey key : keys) {
            Map<String, String> rowValues = values.getOrDefault(key.getKeyId(), Collections.emptyMap());
            if (onlyMissing && codes.stream().allMatch(code -> StringUtils.hasText(rowValues.get(code)))) {
                continue;
            }
            List<String> row = new ArrayList<>();
            row.add(key.getNamespace());
            row.add(key.getKeyCode());
            for (String code : codes) {
                row.add(rowValues.get(code));
            }
            row.add(key.getRemark());
            sb.append(I18nCsvCodec.line(row)).append('\n');
        }
        return sb.toString();
    }

    // ==================== 内部 ====================

    private Map<Long, Map<String, String>> valueMap(List<I18nKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> keyIds = keys.stream().map(I18nKey::getKeyId).collect(Collectors.toList());
        List<I18nValue> values = valueMapper.selectList(new LambdaQueryWrapper<I18nValue>()
                .in(I18nValue::getKeyId, keyIds)
                .eq(I18nValue::getTenantId, 0L));
        Map<Long, Map<String, String>> result = new HashMap<>();
        for (I18nValue value : values) {
            result.computeIfAbsent(value.getKeyId(), k -> new LinkedHashMap<>())
                    .put(value.getLanguageCode(), value.getValueText());
        }
        return result;
    }

    private TranslationRowVO toRow(I18nKey key, Map<String, String> values) {
        TranslationRowVO vo = new TranslationRowVO();
        vo.setKeyId(key.getKeyId());
        vo.setNamespace(key.getNamespace());
        vo.setKeyCode(key.getKeyCode());
        vo.setDefaultValue(key.getDefaultValue());
        vo.setModuleCode(key.getModuleCode());
        vo.setStatus(key.getStatus());
        vo.setRemark(key.getRemark());
        if (values != null) {
            vo.setValues(new LinkedHashMap<>(values));
        }
        return vo;
    }

    private I18nKey findKey(String namespace, String keyCode) {
        return keyMapper.selectOne(new LambdaQueryWrapper<I18nKey>()
                .eq(I18nKey::getNamespace, namespace)
                .eq(I18nKey::getKeyCode, keyCode));
    }

    private I18nKey mustGetKey(Long keyId) {
        I18nKey key = keyId == null ? null : keyMapper.selectById(keyId);
        if (key == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "i18n.key.notfound");
        }
        return key;
    }

    private void assertKeyFree(String namespace, String keyCode, Long excludeId) {
        Long count = keyMapper.selectCount(new LambdaQueryWrapper<I18nKey>()
                .eq(I18nKey::getNamespace, namespace)
                .eq(I18nKey::getKeyCode, keyCode)
                .ne(excludeId != null, I18nKey::getKeyId, excludeId));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.key.code.duplicate");
        }
    }

    private String cell(List<String> row, int index) {
        return index < 0 || index >= row.size() ? null : row.get(index);
    }
}
