package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.web.util.HtmlSanitizer;
import com.lingxiao.modules.cms.dto.CmsContentI18nSaveRequest;
import com.lingxiao.modules.cms.dto.CmsContentI18nVO;
import com.lingxiao.modules.cms.entity.CmsContentI18n;
import com.lingxiao.modules.cms.mapper.CmsContentI18nMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CMS 内容多语言服务。
 *
 * <p><b>只做人工翻译：</b>没有机翻入口，也没有 {@code machine_translated} 字段。
 * 后台可以“复制默认语言内容”当起点，但落地仍是草稿，需要人工改完再发布。</p>
 *
 * <p><b>门户只读已发布：</b>{@link #publishedMap} / {@link #published} 只返回
 * {@code status=1} 的行；没有发布版本时门户回退默认语言，并在 VO 上标
 * {@code translated=false}，而不是给用户看半截译文。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsContentI18nService {

    public static final String OWNER_ARTICLE = "article";
    public static final String OWNER_PAGE = "page";

    public static final String STATUS_DRAFT = "0";
    public static final String STATUS_PUBLISHED = "1";

    private static final long DEFAULT_TENANT_ID = 0L;
    private static final List<String> OWNER_TYPES = Arrays.asList(OWNER_ARTICLE, OWNER_PAGE);
    private static final Pattern LANG_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Za-z]{2,4})?$");

    private final CmsContentI18nMapper i18nMapper;

    /** 某个内容的所有语言版本（后台列表）。 */
    public List<CmsContentI18nVO> list(String ownerType, Long ownerId) {
        String type = normalizeOwnerType(ownerType);
        Long id = requireOwnerId(ownerId);
        return i18nMapper.selectList(new LambdaQueryWrapper<CmsContentI18n>()
                        .eq(CmsContentI18n::getOwnerType, type)
                        .eq(CmsContentI18n::getOwnerId, id)
                        .orderByAsc(CmsContentI18n::getLang))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 保存草稿：已有则更新，没有则新建；保存动作不会直接上线。 */
    @Transactional
    public CmsContentI18nVO saveDraft(String ownerType, Long ownerId, String lang,
                                      CmsContentI18nSaveRequest request) {
        String type = normalizeOwnerType(ownerType);
        Long id = requireOwnerId(ownerId);
        String language = normalizeLang(lang);
        String title = requireText(request.getTitle(), "valid.cms.i18n.title.required", 200);
        String content = StringUtils.hasText(request.getContent())
                ? HtmlSanitizer.clean(request.getContent())
                : null;

        CmsContentI18n existing = find(type, id, language);
        CmsContentI18n entity = existing == null ? new CmsContentI18n() : existing;
        if (existing == null) {
            entity.setTenantId(DEFAULT_TENANT_ID);
            entity.setOwnerType(type);
            entity.setOwnerId(id);
            entity.setLang(language);
            entity.setStatus(STATUS_DRAFT);
        }
        entity.setTitle(title);
        entity.setSummary(trimToNull(request.getSummary()));
        entity.setContent(content);
        entity.setSeoTitle(trimToNull(request.getSeoTitle()));
        entity.setSeoKeywords(trimToNull(request.getSeoKeywords()));
        entity.setSeoDescription(trimToNull(request.getSeoDescription()));

        if (existing == null) {
            i18nMapper.insert(entity);
        } else {
            i18nMapper.updateById(entity);
        }
        log.info("保存内容翻译草稿: ownerType={}, ownerId={}, lang={}", type, id, language);
        return toVO(entity);
    }

    /** 发布该语言版本；没有草稿时返回 1004。 */
    @Transactional
    public void publish(String ownerType, Long ownerId, String lang) {
        CmsContentI18n entity = require(normalizeOwnerType(ownerType), requireOwnerId(ownerId), normalizeLang(lang));
        if (STATUS_PUBLISHED.equals(entity.getStatus())) {
            return;
        }
        entity.setStatus(STATUS_PUBLISHED);
        i18nMapper.updateById(entity);
        log.info("发布内容翻译: ownerType={}, ownerId={}, lang={}",
                entity.getOwnerType(), entity.getOwnerId(), entity.getLang());
    }

    /** 下线该语言版本（回到草稿），门户随即回退默认语言。 */
    @Transactional
    public void unpublish(String ownerType, Long ownerId, String lang) {
        CmsContentI18n entity = require(normalizeOwnerType(ownerType), requireOwnerId(ownerId), normalizeLang(lang));
        entity.setStatus(STATUS_DRAFT);
        i18nMapper.updateById(entity);
    }

    /** 删除该语言版本。 */
    @Transactional
    public void delete(String ownerType, Long ownerId, String lang) {
        CmsContentI18n entity = require(normalizeOwnerType(ownerType), requireOwnerId(ownerId), normalizeLang(lang));
        i18nMapper.deleteById(entity.getI18nId());
    }

    /** 内容彻底删除时清理它的所有语言版本。 */
    @Transactional
    public void deleteByOwner(String ownerType, Long ownerId) {
        if (!StringUtils.hasText(ownerType) || ownerId == null) {
            return;
        }
        i18nMapper.delete(new LambdaQueryWrapper<CmsContentI18n>()
                .eq(CmsContentI18n::getOwnerType, ownerType)
                .eq(CmsContentI18n::getOwnerId, ownerId));
    }

    /** 门户详情：已发布版本；没有则返回 null，由调用方回退默认语言。 */
    public CmsContentI18n published(String ownerType, Long ownerId, String lang) {
        if (!StringUtils.hasText(lang) || ownerId == null) {
            return null;
        }
        String language;
        try {
            language = normalizeLang(lang);
        } catch (ServiceException e) {
            return null;
        }
        return i18nMapper.selectOne(new LambdaQueryWrapper<CmsContentI18n>()
                .eq(CmsContentI18n::getOwnerType, ownerType)
                .eq(CmsContentI18n::getOwnerId, ownerId)
                .eq(CmsContentI18n::getLang, language)
                .eq(CmsContentI18n::getStatus, STATUS_PUBLISHED));
    }

    /** 门户列表：一次查出这一页已发布的译文，避免逐篇查询。 */
    public Map<Long, CmsContentI18n> publishedMap(String ownerType, List<Long> ownerIds, String lang) {
        if (!StringUtils.hasText(lang) || ownerIds == null || ownerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String language;
        try {
            language = normalizeLang(lang);
        } catch (ServiceException e) {
            return Collections.emptyMap();
        }
        List<CmsContentI18n> rows = i18nMapper.selectList(new LambdaQueryWrapper<CmsContentI18n>()
                .eq(CmsContentI18n::getOwnerType, ownerType)
                .in(CmsContentI18n::getOwnerId, ownerIds)
                .eq(CmsContentI18n::getLang, language)
                .eq(CmsContentI18n::getStatus, STATUS_PUBLISHED));
        Map<Long, CmsContentI18n> result = new HashMap<>();
        for (CmsContentI18n row : rows) {
            result.put(row.getOwnerId(), row);
        }
        return result;
    }

    private CmsContentI18n find(String ownerType, Long ownerId, String lang) {
        return i18nMapper.selectOne(new LambdaQueryWrapper<CmsContentI18n>()
                .eq(CmsContentI18n::getOwnerType, ownerType)
                .eq(CmsContentI18n::getOwnerId, ownerId)
                .eq(CmsContentI18n::getLang, lang));
    }

    private CmsContentI18n require(String ownerType, Long ownerId, String lang) {
        CmsContentI18n entity = find(ownerType, ownerId, lang);
        if (entity == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.cms.i18n");
        }
        return entity;
    }

    private CmsContentI18nVO toVO(CmsContentI18n entity) {
        CmsContentI18nVO vo = new CmsContentI18nVO();
        vo.setI18nId(entity.getI18nId());
        vo.setOwnerType(entity.getOwnerType());
        vo.setOwnerId(entity.getOwnerId());
        vo.setLang(entity.getLang());
        vo.setTitle(entity.getTitle());
        vo.setSummary(entity.getSummary());
        vo.setContent(entity.getContent());
        vo.setSeoTitle(entity.getSeoTitle());
        vo.setSeoKeywords(entity.getSeoKeywords());
        vo.setSeoDescription(entity.getSeoDescription());
        vo.setStatus(entity.getStatus());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private String normalizeOwnerType(String ownerType) {
        String value = ownerType == null ? "" : ownerType.trim().toLowerCase(Locale.ROOT);
        if (!OWNER_TYPES.contains(value)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.cms.i18n.ownerType");
        }
        return value;
    }


    private String normalizeLang(String lang) {
        String value = lang == null ? "" : lang.trim();
        if (!LANG_PATTERN.matcher(value).matches()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.cms.i18n.lang");
        }
        // 门户 URL 用的是短码（en / zh-cn），库里存完整 locale；这里统一归一
        String lower = value.toLowerCase(Locale.ROOT);
        if ("en".equals(lower)) {
            return "en-US";
        }
        if ("zh".equals(lower) || "zh-cn".equals(lower)) {
            return "zh-CN";
        }
        if ("zh-CN".equalsIgnoreCase(value)) {
            return "zh-CN";
        }
        String[] parts = value.split("-");
        return parts[0].toLowerCase(Locale.ROOT)
                + (parts.length > 1 ? "-" + parts[1].toUpperCase(Locale.ROOT) : "");
    }

    private Long requireOwnerId(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.cms.i18n.ownerId");
        }
        return ownerId;
    }

    private String requireText(String text, String key, int max) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, key);
        }
        if (value.length() > max) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, key);
        }
        return value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

}