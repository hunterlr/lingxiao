package com.lingxiao.modules.i18n.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.i18n.dto.PackPublishRequest;
import com.lingxiao.modules.i18n.dto.PackRollbackRequest;
import com.lingxiao.modules.i18n.dto.PackVO;
import com.lingxiao.modules.i18n.dto.PortalLanguageVO;
import com.lingxiao.modules.i18n.dto.PortalPackVO;
import com.lingxiao.modules.i18n.entity.I18nKey;
import com.lingxiao.modules.i18n.entity.I18nLanguage;
import com.lingxiao.modules.i18n.entity.I18nPack;
import com.lingxiao.modules.i18n.entity.I18nValue;
import com.lingxiao.modules.i18n.mapper.I18nKeyMapper;
import com.lingxiao.modules.i18n.mapper.I18nPackMapper;
import com.lingxiao.modules.i18n.mapper.I18nValueMapper;
import com.lingxiao.modules.i18n.message.DatabaseTranslationProvider;
import com.lingxiao.modules.i18n.support.I18nLanguageCodes;
import com.lingxiao.modules.i18n.support.I18nPlaceholders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 语言包发布 / 回滚 / 门户下发。
 *
 * <p>发布粒度是 {@code languageCode + namespace}：</p>
 * <ol>
 *   <li>校验占位符；</li>
 *   <li>生成新版本并写 {@code snapshot_json}；</li>
 *   <li>同语言同命名空间的旧版本降为历史；</li>
 *   <li>把参与发布的译文标记为已发布；</li>
 *   <li>清本地缓存，5 秒内全站生效。</li>
 * </ol>
 *
 * <p>回滚按快照恢复 {@code i18n_value}，并把目标版本重新标为当前，
 * 所以“回滚的是译文”，不是只改一个版本号。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nPackService {

    private static final String STATUS_CURRENT = "1";

    private static final String STATUS_HISTORY = "0";

    private final I18nLanguageService languageService;

    private final I18nKeyMapper keyMapper;

    private final I18nValueMapper valueMapper;

    private final I18nPackMapper packMapper;

    private final DatabaseTranslationProvider translationProvider;

    private final ObjectMapper objectMapper;

    // ==================== 后台 ====================

    public List<PackVO> list(String languageCode, String namespace) {
        List<I18nPack> packs = packMapper.selectList(new LambdaQueryWrapper<I18nPack>()
                .eq(StringUtils.hasText(languageCode), I18nPack::getLanguageCode,
                        I18nLanguageCodes.normalize(languageCode))
                .eq(StringUtils.hasText(namespace), I18nPack::getNamespace, namespace)
                .eq(I18nPack::getTenantId, 0L)
                .orderByDesc(I18nPack::getVersion)
                .orderByDesc(I18nPack::getPublishedAt));
        return packs.stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<PackVO> current(String languageCode, String namespace) {
        List<I18nPack> packs = packMapper.selectList(new LambdaQueryWrapper<I18nPack>()
                .eq(StringUtils.hasText(languageCode), I18nPack::getLanguageCode,
                        I18nLanguageCodes.normalize(languageCode))
                .eq(StringUtils.hasText(namespace), I18nPack::getNamespace, namespace)
                .eq(I18nPack::getTenantId, 0L)
                .eq(I18nPack::getStatus, STATUS_CURRENT)
                .orderByAsc(I18nPack::getNamespace));
        return packs.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public List<PackVO> publish(PackPublishRequest request, Long operatorId) {
        I18nLanguage language = languageService.mustGetByCode(request.getLanguageCode());
        if (!"0".equals(language.getStatus())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.value.language.disabled");
        }
        List<String> namespaces = resolveNamespaces(language.getLanguageCode(), request.getNamespace());
        if (namespaces.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.pack.empty");
        }
        List<PackVO> result = new ArrayList<>();
        for (String namespace : namespaces) {
            PackVO published = publishNamespace(language.getLanguageCode(), namespace, operatorId);
            if (published != null) {
                result.add(published);
            }
        }
        if (result.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.pack.empty");
        }
        translationProvider.invalidate();
        log.info("发布语言包: language={}, namespaces={}, operator={}",
                language.getLanguageCode(), result.size(), operatorId);
        return result;
    }

    private List<String> resolveNamespaces(String languageCode, String namespace) {
        if (StringUtils.hasText(namespace)) {
            return Collections.singletonList(namespace.trim());
        }
        List<String> namespaces = valueMapper.selectNamespacesWithValues(languageCode);
        return namespaces == null ? Collections.emptyList() : namespaces;
    }

    private PackVO publishNamespace(String languageCode, String namespace, Long operatorId) {
        List<I18nKey> keys = keyMapper.selectList(new LambdaQueryWrapper<I18nKey>()
                .eq(I18nKey::getNamespace, namespace)
                .eq(I18nKey::getStatus, "0")
                .orderByAsc(I18nKey::getKeyCode));
        if (keys.isEmpty()) {
            return null;
        }
        Map<Long, I18nKey> keyMap = keys.stream()
                .collect(Collectors.toMap(I18nKey::getKeyId, item -> item, (a, b) -> a));
        List<I18nValue> values = valueMapper.selectList(new LambdaQueryWrapper<I18nValue>()
                .eq(I18nValue::getTenantId, 0L)
                .eq(I18nValue::getLanguageCode, languageCode)
                .in(I18nValue::getKeyId, keyMap.keySet()));

        Map<Long, String> snapshot = new LinkedHashMap<>();
        for (I18nValue value : values) {
            if (!StringUtils.hasText(value.getValueText())) {
                continue;
            }
            I18nKey key = keyMap.get(value.getKeyId());
            if (key == null) {
                continue;
            }
            if (key.getDefaultValue() != null
                    && !I18nPlaceholders.sameAs(key.getDefaultValue(), value.getValueText())) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.value.placeholder.mismatch");
            }
            snapshot.put(key.getKeyId(), value.getValueText());
        }
        if (snapshot.isEmpty()) {
            return null;
        }

        int version = nextVersion(languageCode, namespace);
        String checksum = checksum(keyMap, snapshot);
        packMapper.update(null, new LambdaUpdateWrapper<I18nPack>()
                .eq(I18nPack::getTenantId, 0L)
                .eq(I18nPack::getLanguageCode, languageCode)
                .eq(I18nPack::getNamespace, namespace)
                .eq(I18nPack::getStatus, STATUS_CURRENT)
                .set(I18nPack::getStatus, STATUS_HISTORY));

        LocalDateTime now = LocalDateTime.now();
        I18nPack pack = new I18nPack();
        pack.setTenantId(0L);
        pack.setLanguageCode(languageCode);
        pack.setNamespace(namespace);
        pack.setVersion(version);
        pack.setChecksum(checksum);
        pack.setStatus(STATUS_CURRENT);
        pack.setSnapshotJson(writeSnapshot(snapshot));
        pack.setPublishedBy(operatorId);
        pack.setPublishedAt(now);
        packMapper.insert(pack);

        valueMapper.update(null, new LambdaUpdateWrapper<I18nValue>()
                .eq(I18nValue::getTenantId, 0L)
                .eq(I18nValue::getLanguageCode, languageCode)
                .in(I18nValue::getKeyId, snapshot.keySet())
                .set(I18nValue::getReviewStatus, "1")
                // 工作副本 -> 发布值；线上只读 published_text，草稿不会提前生效
                .setSql("published_text = value_text")
                .set(I18nValue::getUpdateTime, now));
        return toVO(pack);
    }

    @Transactional
    public PackVO rollback(PackRollbackRequest request, Long operatorId) {
        I18nPack target = packMapper.selectById(request.getPackId());
        if (target == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "i18n.pack.notfound");
        }
        Map<String, String> snapshot = readSnapshot(target.getSnapshotJson());
        LocalDateTime now = LocalDateTime.now();
        int restored = 0;
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            Long keyId;
            try {
                keyId = Long.valueOf(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            I18nValue value = valueMapper.selectOne(new LambdaQueryWrapper<I18nValue>()
                    .eq(I18nValue::getKeyId, keyId)
                    .eq(I18nValue::getTenantId, 0L)
                    .eq(I18nValue::getLanguageCode, target.getLanguageCode()));
            if (value == null) {
                value = new I18nValue();
                value.setKeyId(keyId);
                value.setTenantId(0L);
                value.setLanguageCode(target.getLanguageCode());
                value.setValueText(entry.getValue());
                value.setPublishedText(entry.getValue());
                value.setReviewStatus("1");
                value.setVersion(1);
                value.setTranslatedBy(operatorId);
                value.setTranslatedAt(now);
                value.setCreateTime(now);
                valueMapper.insert(value);
            } else {
                value.setValueText(entry.getValue());
                value.setPublishedText(entry.getValue());
                value.setReviewStatus("1");
                value.setVersion(value.getVersion() == null ? 1 : value.getVersion() + 1);
                value.setTranslatedBy(operatorId);
                value.setTranslatedAt(now);
                value.setUpdateTime(now);
                valueMapper.updateById(value);
            }
            restored++;
        }

        packMapper.update(null, new LambdaUpdateWrapper<I18nPack>()
                .eq(I18nPack::getTenantId, 0L)
                .eq(I18nPack::getLanguageCode, target.getLanguageCode())
                .eq(I18nPack::getNamespace, target.getNamespace())
                .set(I18nPack::getStatus, STATUS_HISTORY));
        packMapper.update(null, new LambdaUpdateWrapper<I18nPack>()
                .eq(I18nPack::getPackId, target.getPackId())
                .set(I18nPack::getStatus, STATUS_CURRENT));
        target.setStatus(STATUS_CURRENT);

        translationProvider.invalidate();
        log.info("回滚语言包: packId={}, language={}, namespace={}, version={}, restored={}, operator={}",
                target.getPackId(), target.getLanguageCode(), target.getNamespace(),
                target.getVersion(), restored, operatorId);
        return toVO(target);
    }

    // ==================== 门户公开接口 ====================

    public List<PortalLanguageVO> portalLanguages() {
        List<I18nLanguage> languages = languageService.options();
        List<PortalLanguageVO> result = new ArrayList<>(languages.size());
        for (I18nLanguage language : languages) {
            PortalLanguageVO vo = new PortalLanguageVO();
            vo.setLanguageCode(language.getLanguageCode());
            vo.setLanguageName(language.getLanguageName());
            vo.setNativeName(language.getNativeName());
            vo.setDirection(language.getDirection());
            vo.setDefault(Integer.valueOf(1).equals(language.getIsDefault()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 门户整包：只返回已发布译文，按命名空间前缀过滤。
     */
    public PortalPackVO portalPack(String languageCode, String namespace) {
        I18nLanguage language = languageService.mustGetByCode(languageCode);
        if (!"0".equals(language.getStatus())) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "i18n.language.notfound");
        }
        String ns = StringUtils.hasText(namespace) ? namespace.trim() : null;
        Map<String, String> messages = new LinkedHashMap<>();
        String prefix = ns == null ? null : ns + ".";
        translationProvider.published(language.getLanguageCode()).forEach((key, value) -> {
            if (prefix == null || key.startsWith(prefix)) {
                messages.put(key, value);
            }
        });
        I18nPack pack = currentPack(language.getLanguageCode(), ns);
        PortalPackVO vo = new PortalPackVO();
        vo.setLanguageCode(language.getLanguageCode());
        vo.setNamespace(ns);
        vo.setMessages(messages);
        if (pack != null) {
            vo.setVersion(pack.getVersion());
            vo.setChecksum(pack.getChecksum());
        }
        return vo;
    }

    public PackVO portalVersion(String languageCode, String namespace) {
        I18nLanguage language = languageService.mustGetByCode(languageCode);
        I18nPack pack = currentPack(language.getLanguageCode(),
                StringUtils.hasText(namespace) ? namespace.trim() : null);
        if (pack == null) {
            return null;
        }
        return toVO(pack);
    }

    private I18nPack currentPack(String languageCode, String namespace) {
        return packMapper.selectOne(new LambdaQueryWrapper<I18nPack>()
                .eq(I18nPack::getTenantId, 0L)
                .eq(I18nPack::getLanguageCode, languageCode)
                .eq(StringUtils.hasText(namespace), I18nPack::getNamespace, namespace)
                .eq(I18nPack::getStatus, STATUS_CURRENT)
                .orderByDesc(I18nPack::getVersion)
                .last("LIMIT 1"));
    }

    // ==================== 内部 ====================

    private int nextVersion(String languageCode, String namespace) {
        I18nPack last = packMapper.selectOne(new LambdaQueryWrapper<I18nPack>()
                .eq(I18nPack::getTenantId, 0L)
                .eq(I18nPack::getLanguageCode, languageCode)
                .eq(I18nPack::getNamespace, namespace)
                .orderByDesc(I18nPack::getVersion)
                .last("LIMIT 1"));
        return last == null || last.getVersion() == null ? 1 : last.getVersion() + 1;
    }

    private String checksum(Map<Long, I18nKey> keyMap, Map<Long, String> snapshot) {
        List<String> lines = new ArrayList<>();
        snapshot.forEach((keyId, text) -> {
            I18nKey key = keyMap.get(keyId);
            if (key != null) {
                lines.add(key.getKeyCode() + "=" + text);
            }
        });
        Collections.sort(lines);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String writeSnapshot(Map<Long, String> snapshot) {
        Map<String, String> serializable = new LinkedHashMap<>();
        snapshot.forEach((keyId, text) -> serializable.put(String.valueOf(keyId), text));
        try {
            return objectMapper.writeValueAsString(serializable);
        } catch (JsonProcessingException e) {
            throw ServiceException.keyed(ErrorCode.INTERNAL_ERROR, "i18n.pack.snapshot.invalid");
        }
    }

    private Map<String, String> readSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.pack.snapshot.invalid");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "i18n.pack.snapshot.invalid");
        }
    }

    private PackVO toVO(I18nPack pack) {
        PackVO vo = new PackVO();
        vo.setPackId(pack.getPackId());
        vo.setLanguageCode(pack.getLanguageCode());
        vo.setNamespace(pack.getNamespace());
        vo.setVersion(pack.getVersion());
        vo.setChecksum(pack.getChecksum());
        vo.setStatus(pack.getStatus());
        vo.setPublishedBy(pack.getPublishedBy());
        vo.setPublishedAt(pack.getPublishedAt());
        return vo;
    }
}
