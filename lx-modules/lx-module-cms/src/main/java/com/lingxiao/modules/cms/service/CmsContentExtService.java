package com.lingxiao.modules.cms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.modules.cms.entity.CmsContentExt;
import com.lingxiao.modules.cms.mapper.CmsContentExtMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内容扩展字段读写。
 *
 * <p>扩展字段统一存 {@code cms_content_ext.ext_json}，按
 * {@code owner_type + owner_id} 唯一。这里只负责 JSON 序列化 / 反序列化，
 * “哪些字段、是否必填”由 {@link CmsModelService} 校验。</p>
 *
 * <p>JSON 解析失败按空对象处理并记日志：手工改库或升级遗留脏数据
 * 不应让整篇内容打不开。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsContentExtService {

    /** 扩展值统一用 JSON 对象；数组 / 标量根节点不合法。 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CmsContentExtMapper extMapper;

    /** 读取扩展字段；不存在或脏数据返回空 Map。 */
    public Map<String, Object> load(String ownerType, Long ownerId) {
        if (!StringUtils.hasText(ownerType) || ownerId == null) {
            return new LinkedHashMap<>();
        }
        CmsContentExt ext = extMapper.selectByOwner(ownerType, ownerId);
        return ext == null ? new LinkedHashMap<>() : parse(ext.getExtJson());
    }

    /**
     * 保存扩展字段；空对象等价于清空。
     *
     * <p>先查再写而不是依赖唯一键 INSERT ... ON DUPLICATE KEY：逻辑删除后
     * 唯一键仍然存在，先查后写能显式复用同一行。</p>
     */
    public void save(String ownerType, Long ownerId, Long siteId, Long modelId,
                     Map<String, Object> ext) {
        if (!StringUtils.hasText(ownerType) || ownerId == null) {
            return;
        }
        if (ext == null || ext.isEmpty()) {
            extMapper.deleteByOwner(ownerType, ownerId);
            return;
        }
        String json = write(ext);
        CmsContentExt existing = extMapper.selectByOwner(ownerType, ownerId);
        if (existing == null) {
            CmsContentExt row = new CmsContentExt();
            row.setSiteId(siteId);
            row.setOwnerType(ownerType);
            row.setOwnerId(ownerId);
            row.setModelId(modelId);
            row.setExtJson(json);
            extMapper.insert(row);
        } else {
            // 逻辑删除过的旧行先恢复，避免唯一键冲突
            existing.setDelFlag("0");
            existing.setSiteId(siteId);
            existing.setModelId(modelId);
            existing.setExtJson(json);
            extMapper.updateById(existing);
        }
    }

    public void deleteByOwner(String ownerType, Long ownerId) {
        if (!StringUtils.hasText(ownerType) || ownerId == null) {
            return;
        }
        extMapper.deleteByOwner(ownerType, ownerId);
    }

    /** 解析 JSON 对象；失败返回空 Map，不抛异常。 */
    public static Map<String, Object> parse(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> value = MAPPER.readValue(json, MAP_TYPE);
            return value == null ? new LinkedHashMap<>() : value;
        } catch (Exception e) {
            log.warn("CMS 扩展字段 JSON 解析失败，按空对象处理：{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** 序列化扩展字段；失败抛运行时异常，避免写入半截数据。 */
    public static String write(Map<String, Object> ext) {
        try {
            return MAPPER.writeValueAsString(ext == null ? Collections.emptyMap() : ext);
        } catch (Exception e) {
            throw new IllegalStateException("CMS 扩展字段序列化失败", e);
        }
    }
}
