package com.lingxiao.modules.i18n.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.i18n.dto.LanguageRequest;
import com.lingxiao.modules.i18n.entity.I18nKey;
import com.lingxiao.modules.i18n.entity.I18nLanguage;
import com.lingxiao.modules.i18n.entity.I18nPack;
import com.lingxiao.modules.i18n.entity.I18nValue;
import com.lingxiao.modules.i18n.mapper.I18nKeyMapper;
import com.lingxiao.modules.i18n.mapper.I18nLanguageMapper;
import com.lingxiao.modules.i18n.mapper.I18nPackMapper;
import com.lingxiao.modules.i18n.mapper.I18nValueMapper;
import com.lingxiao.modules.i18n.message.DatabaseTranslationProvider;
import com.lingxiao.modules.i18n.support.I18nLanguageCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 语言管理。
 *
 * <p>约束：</p>
 * <ul>
 *   <li>语言编码唯一，保存前统一归一为 BCP 47；</li>
 *   <li>默认语言只能有一个；默认语言不能停用；</li>
 *   <li>已有译文或语言包的语言不允许删除，避免译文变成孤儿。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nLanguageService {

    private final I18nLanguageMapper languageMapper;

    private final I18nKeyMapper keyMapper;

    private final I18nValueMapper valueMapper;

    private final I18nPackMapper packMapper;

    private final DatabaseTranslationProvider translationProvider;

    public List<I18nLanguage> list(String keyword, String status) {
        LambdaQueryWrapper<I18nLanguage> wrapper = new LambdaQueryWrapper<I18nLanguage>()
                .eq(StringUtils.hasText(status), I18nLanguage::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(I18nLanguage::getLanguageCode, keyword)
                        .or().like(I18nLanguage::getLanguageName, keyword)
                        .or().like(I18nLanguage::getNativeName, keyword))
                .orderByAsc(I18nLanguage::getSort)
                .orderByAsc(I18nLanguage::getLanguageId);
        return languageMapper.selectList(wrapper);
    }

    public List<I18nLanguage> options() {
        return languageMapper.selectList(new LambdaQueryWrapper<I18nLanguage>()
                .eq(I18nLanguage::getStatus, "0")
                .orderByAsc(I18nLanguage::getSort)
                .orderByAsc(I18nLanguage::getLanguageId));
    }

    @Transactional
    public Long create(LanguageRequest request) {
        String code = I18nLanguageCodes.normalize(request.getLanguageCode());
        assertCodeFree(code, null);
        boolean first = languageMapper.selectCount(null) == 0;
        boolean isDefault = first || Integer.valueOf(1).equals(request.getIsDefault());
        if (isDefault) {
            clearDefault();
        }
        I18nLanguage language = new I18nLanguage();
        language.setLanguageCode(code);
        language.setLanguageName(request.getLanguageName().trim());
        language.setNativeName(StringUtils.hasText(request.getNativeName())
                ? request.getNativeName().trim() : language.getLanguageName());
        language.setLocale(StringUtils.hasText(request.getLocale())
                ? request.getLocale().trim() : code.replace('-', '_'));
        language.setDirection(StringUtils.hasText(request.getDirection()) ? request.getDirection() : "ltr");
        language.setIsDefault(isDefault ? 1 : 0);
        // 第一个语言一定启用；后续默认启用，管理员可再停用
        language.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "0");
        language.setSort(request.getSort() == null ? 0 : request.getSort());
        language.setCreateTime(LocalDateTime.now());
        languageMapper.insert(language);
        translationProvider.invalidate();
        log.info("新增语言: code={}, default={}", code, isDefault);
        return language.getLanguageId();
    }

    @Transactional
    public void update(Long languageId, LanguageRequest request) {
        I18nLanguage existing = mustGet(languageId);
        String code = I18nLanguageCodes.normalize(request.getLanguageCode());
        if (!existing.getLanguageCode().equals(code)) {
            if (hasTranslations(existing.getLanguageCode()) || hasPacks(existing.getLanguageCode())) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.language.inuse");
            }
            assertCodeFree(code, languageId);
        }
        boolean isDefault = Integer.valueOf(1).equals(request.getIsDefault());
        if (isDefault && !Integer.valueOf(1).equals(existing.getIsDefault())) {
            clearDefault();
        }
        if (!isDefault && Integer.valueOf(1).equals(existing.getIsDefault())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.language.default.locked");
        }
        existing.setLanguageCode(code);
        existing.setLanguageName(request.getLanguageName().trim());
        existing.setNativeName(StringUtils.hasText(request.getNativeName())
                ? request.getNativeName().trim() : existing.getLanguageName());
        existing.setLocale(StringUtils.hasText(request.getLocale())
                ? request.getLocale().trim() : code.replace('-', '_'));
        existing.setDirection(StringUtils.hasText(request.getDirection()) ? request.getDirection() : "ltr");
        existing.setIsDefault(isDefault ? 1 : 0);
        existing.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : existing.getStatus());
        existing.setSort(request.getSort() == null ? existing.getSort() : request.getSort());
        existing.setUpdateTime(LocalDateTime.now());
        languageMapper.updateById(existing);
        translationProvider.invalidate();
    }

    @Transactional
    public void delete(Long languageId) {
        I18nLanguage existing = mustGet(languageId);
        if (Integer.valueOf(1).equals(existing.getIsDefault())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.language.default.locked");
        }
        if (hasTranslations(existing.getLanguageCode()) || hasPacks(existing.getLanguageCode())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.language.inuse");
        }
        languageMapper.deleteById(languageId);
    }

    @Transactional
    public void changeStatus(Long languageId, String status) {
        I18nLanguage existing = mustGet(languageId);
        if ("1".equals(status) && Integer.valueOf(1).equals(existing.getIsDefault())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.language.default.locked");
        }
        languageMapper.update(null, new LambdaUpdateWrapper<I18nLanguage>()
                .eq(I18nLanguage::getLanguageId, languageId)
                .set(I18nLanguage::getStatus, status)
                .set(I18nLanguage::getUpdateTime, LocalDateTime.now()));
        translationProvider.invalidate();
    }

    /** 已启用的语言编码集合；发布 / 导入前校验用。 */
    public List<String> enabledCodes() {
        return options().stream().map(I18nLanguage::getLanguageCode).collect(java.util.stream.Collectors.toList());
    }

    public I18nLanguage mustGet(Long languageId) {
        I18nLanguage language = languageId == null ? null : languageMapper.selectById(languageId);
        if (language == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "i18n.language.notfound");
        }
        return language;
    }

    public I18nLanguage mustGetByCode(String languageCode) {
        String code = I18nLanguageCodes.normalize(languageCode);
        I18nLanguage language = code == null ? null : languageMapper.selectOne(
                new LambdaQueryWrapper<I18nLanguage>().eq(I18nLanguage::getLanguageCode, code));
        if (language == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "i18n.language.notfound");
        }
        return language;
    }

    private void assertCodeFree(String code, Long excludeId) {
        Long count = languageMapper.selectCount(new LambdaQueryWrapper<I18nLanguage>()
                .eq(I18nLanguage::getLanguageCode, code)
                .ne(excludeId != null, I18nLanguage::getLanguageId, excludeId));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.language.code.duplicate");
        }
    }

    private void clearDefault() {
        languageMapper.update(null, new LambdaUpdateWrapper<I18nLanguage>()
                .eq(I18nLanguage::getIsDefault, 1)
                .set(I18nLanguage::getIsDefault, 0)
                .set(I18nLanguage::getUpdateTime, LocalDateTime.now()));
    }

    private boolean hasTranslations(String languageCode) {
        Long count = valueMapper.selectCount(new LambdaQueryWrapper<I18nValue>()
                .eq(I18nValue::getLanguageCode, languageCode));
        return count != null && count > 0;
    }

    private boolean hasPacks(String languageCode) {
        Long count = packMapper.selectCount(new LambdaQueryWrapper<I18nPack>()
                .eq(I18nPack::getLanguageCode, languageCode));
        return count != null && count > 0;
    }

    /** 键数量统计：语言页“已覆盖命名空间”展示用，保持与 key 表一致。 */
    public long countKeys() {
        Long count = keyMapper.selectCount(null);
        return count == null ? 0 : count;
    }
}
