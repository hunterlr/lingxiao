package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsModel;
import com.lingxiao.modules.cms.entity.CmsModelField;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsContentExtMapper;
import com.lingxiao.modules.cms.mapper.CmsModelFieldMapper;
import com.lingxiao.modules.cms.mapper.CmsModelMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * CMS 内容模型服务。
 *
 * <p><b>站点级模型：</b>每个站点有 article / gallery / page 三个内置模型，
 * 由 {@link #ensureBuiltIn(Long)} 懒初始化（迁移只为历史站点补一遍）。
 * 栏目绑定 article / gallery；单页固定 page。</p>
 *
 * <p><b>字段值不落主表：</b>字段定义在 {@code cms_model_field}，值统一存
 * {@code cms_content_ext.ext_json}，新增字段不需要改业务表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsModelService {

    public static final String TYPE_ARTICLE = "article";
    public static final String TYPE_GALLERY = "gallery";
    public static final String TYPE_PAGE = "page";

    public static final String BUILT_IN_YES = "1";
    public static final String BUILT_IN_NO = "0";

    /** 最小字段类型集；前端按这个集合渲染表单控件。 */
    private static final Set<String> FIELD_TYPES = new HashSet<>(Arrays.asList(
            "text", "textarea", "richtext", "number", "date",
            "select", "checkbox", "image", "images", "file"));

    /** 需要选项配置的字段类型。 */
    private static final Set<String> OPTION_TYPES = new HashSet<>(Arrays.asList("select", "checkbox"));

    /** 多值字段类型。 */
    private static final Set<String> MULTI_VALUE_TYPES = new HashSet<>(Arrays.asList("checkbox", "images"));

    private final CmsModelMapper modelMapper;
    private final CmsModelFieldMapper fieldMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsPageMapper pageMapper;
    private final CmsContentExtMapper extMapper;
    private final CmsSiteService siteService;

    /**
     * 懒初始化站点内置模型。
     *
     * <p>可重复调用：已存在的 key 直接跳过；字段也按 key 去重。
     * 因此迁移已补过的站点不会被重复插入。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureBuiltIn(Long siteId) {
        if (siteId == null) {
            return;
        }
        ensureModel(siteId, TYPE_ARTICLE, "文章", 1,
                Arrays.asList(
                        field("subtitle", "副标题", "text", false, false, false, 1),
                        field("seoKeywords", "SEO 关键词", "text", false, false, false, 2),
                        field("seoDescription", "SEO 描述", "textarea", false, false, false, 3)));
        ensureModel(siteId, TYPE_GALLERY, "图集", 2,
                Arrays.asList(
                        field("images", "图集图片", "images", true, true, false, 1),
                        field("photographer", "摄影师", "text", false, false, false, 2),
                        field("shotAt", "拍摄日期", "date", false, false, false, 3)));
        ensureModel(siteId, TYPE_PAGE, "单页", 3,
                Arrays.asList(
                        field("subtitle", "副标题", "text", false, false, false, 1),
                        field("contact", "联系方式", "textarea", false, false, false, 2)));
    }

    /** 站点模型列表；后台模型管理页使用，含停用模型。 */
    public List<CmsModel> list(Long siteId, String status) {
        siteService.mustGet(siteId);
        ensureBuiltIn(siteId);
        return modelMapper.selectList(new LambdaQueryWrapper<CmsModel>()
                .eq(CmsModel::getSiteId, siteId)
                .eq(StringUtils.hasText(status), CmsModel::getStatus, status)
                .orderByAsc(CmsModel::getSort)
                .orderByAsc(CmsModel::getModelId));
    }

    /** 下拉用：只返回启用模型。 */
    public List<CmsModel> options(Long siteId) {
        return list(siteId, CommonConstants.STATUS_NORMAL);
    }

    /** 模型详情：回填字段定义。 */
    public CmsModel detail(Long modelId) {
        CmsModel model = mustGet(modelId);
        model.setFields(fields(modelId));
        return model;
    }

    public CmsModel mustGet(Long modelId) {
        CmsModel model = modelId == null ? null : modelMapper.selectById(modelId);
        if (model == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.model.notfound");
        }
        return model;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CmsModel model) {
        siteService.mustGet(model.getSiteId());
        String modelKey = model.getModelKey().trim();
        ensureKeyUnique(model.getSiteId(), modelKey, null);
        model.setModelId(null);
        model.setModelKey(modelKey);
        model.setModelName(model.getModelName().trim());
        model.setModelType(validateType(model.getModelType()));
        model.setBuiltIn(BUILT_IN_NO);
        model.setStatus(defaultStatus(model.getStatus()));
        model.setSort(model.getSort() == null ? 0 : model.getSort());
        model.setCreateBy(currentUserId());
        modelMapper.insert(model);
        return model.getModelId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long modelId, CmsModel model) {
        CmsModel existing = mustGet(modelId);
        String modelKey = model.getModelKey().trim();
        ensureKeyUnique(existing.getSiteId(), modelKey, modelId);
        if (BUILT_IN_YES.equals(existing.getBuiltIn())) {
            // 内置模型的 key / 类型是代码约定，只允许改展示名、排序、状态与备注
            existing.setModelName(model.getModelName().trim());
            existing.setSort(model.getSort() == null ? existing.getSort() : model.getSort());
            existing.setStatus(defaultStatus(model.getStatus()));
            existing.setRemark(model.getRemark());
        } else {
            existing.setModelKey(modelKey);
            existing.setModelName(model.getModelName().trim());
            existing.setModelType(validateType(model.getModelType()));
            existing.setSort(model.getSort() == null ? existing.getSort() : model.getSort());
            existing.setStatus(defaultStatus(model.getStatus()));
            existing.setRemark(model.getRemark());
        }
        existing.setUpdateBy(currentUserId());
        modelMapper.updateById(existing);
    }

    /** 删除模型：内置模型、被栏目 / 单页 / 扩展数据引用时都拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long modelId) {
        CmsModel model = mustGet(modelId);
        if (BUILT_IN_YES.equals(model.getBuiltIn())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.model.builtin.undeletable");
        }
        long categories = countCategories(modelId);
        long pages = countPages(modelId);
        long extRows = extMapper.countByModel(modelId);
        if (categories > 0 || pages > 0 || extRows > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.model.referenced");
        }
        fieldMapper.delete(new LambdaQueryWrapper<CmsModelField>()
                .eq(CmsModelField::getModelId, modelId));
        modelMapper.deleteById(modelId);
    }

    /* ==================== 字段定义 ==================== */

    public List<CmsModelField> fields(Long modelId) {
        mustGet(modelId);
        return fieldMapper.selectList(new LambdaQueryWrapper<CmsModelField>()
                .eq(CmsModelField::getModelId, modelId)
                .orderByAsc(CmsModelField::getSort)
                .orderByAsc(CmsModelField::getFieldId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addField(Long modelId, CmsModelField field) {
        CmsModel model = mustGet(modelId);
        String fieldKey = field.getFieldKey().trim();
        ensureFieldKeyUnique(modelId, fieldKey, null);
        validateFieldType(field);
        field.setFieldId(null);
        field.setModelId(modelId);
        field.setSiteId(model.getSiteId());
        field.setFieldKey(fieldKey);
        field.setFieldName(field.getFieldName().trim());
        field.setRequired(defaultFlag(field.getRequired()));
        field.setListVisible(defaultFlag(field.getListVisible()));
        field.setSearchable(defaultFlag(field.getSearchable()));
        field.setStatus(defaultStatus(field.getStatus()));
        field.setSort(field.getSort() == null ? 0 : field.getSort());
        field.setCreateBy(currentUserId());
        fieldMapper.insert(field);
        return field.getFieldId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateField(Long modelId, Long fieldId, CmsModelField field) {
        CmsModelField existing = mustField(modelId, fieldId);
        String fieldKey = field.getFieldKey().trim();
        ensureFieldKeyUnique(modelId, fieldKey, fieldId);
        validateFieldType(field);
        existing.setFieldKey(fieldKey);
        existing.setFieldName(field.getFieldName().trim());
        existing.setFieldType(field.getFieldType());
        existing.setRequired(defaultFlag(field.getRequired()));
        existing.setDefaultValue(field.getDefaultValue());
        existing.setOptionsJson(field.getOptionsJson());
        existing.setValidationJson(field.getValidationJson());
        existing.setListVisible(defaultFlag(field.getListVisible()));
        existing.setSearchable(defaultFlag(field.getSearchable()));
        existing.setSort(field.getSort() == null ? existing.getSort() : field.getSort());
        existing.setStatus(defaultStatus(field.getStatus()));
        existing.setRemark(field.getRemark());
        existing.setUpdateBy(currentUserId());
        fieldMapper.updateById(existing);
    }

    /**
     * 删除字段定义。
     *
     * <p>用物理删除：逻辑删除会占着唯一键，导致同名字段无法重新添加。
     * 历史扩展 JSON 里对应值保留，重新加同名字段即可恢复展示。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeField(Long modelId, Long fieldId) {
        mustField(modelId, fieldId);
        fieldMapper.hardDeleteById(fieldId);
    }

    /* ==================== 内容侧解析 / 校验 ==================== */

    /**
     * 解析栏目使用的模型：栏目显式绑定优先，空值回退站点内置文章模型。
     *
     * <p>历史栏目在 V35 已回填；这里兜底兼容直接改库 / 迁移未跑的极端情况。</p>
     */
    public CmsModel resolveForCategory(CmsCategory category) {
        if (category == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.model.notfound");
        }
        if (category.getModelId() != null) {
            CmsModel bound = mustGet(category.getModelId());
            if (!Objects.equals(bound.getSiteId(), category.getSiteId())) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.model.notfound");
            }
            return bound;
        }
        return findOrEnsureBuiltIn(category.getSiteId(), TYPE_ARTICLE);
    }

    /** 单页固定使用 page 模型；缺失时懒初始化。 */
    public CmsModel resolveForPage(Long siteId) {
        return findOrEnsureBuiltIn(siteId, TYPE_PAGE);
    }

    public CmsModel findOrEnsureBuiltIn(Long siteId, String type) {
        CmsModel model = selectByKey(siteId, type);
        if (model == null) {
            ensureBuiltIn(siteId);
            model = selectByKey(siteId, type);
        }
        if (model == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.model.notfound");
        }
        return model;
    }

    /**
     * 校验并规整扩展字段。
     *
     * <p>只保留当前模型已定义字段；必填缺失、多值类型不符直接拒绝。
     * 必填只在保存内容时强制，读取历史数据不校验，避免老内容打不开。</p>
     */
    public Map<String, Object> validateExt(Long modelId, Map<String, Object> ext, boolean enforceRequired) {
        Map<String, Object> source = ext == null ? Collections.emptyMap() : ext;
        Map<String, Object> result = new LinkedHashMap<>();
        for (CmsModelField field : fields(modelId)) {
            if (!CommonConstants.STATUS_NORMAL.equals(field.getStatus())) {
                continue;
            }
            Object value = source.get(field.getFieldKey());
            if (isEmptyValue(value)) {
                if (enforceRequired && isYes(field.getRequired())) {
                    throw ServiceException.keyed(ErrorCode.BAD_REQUEST,
                            "cms.field.required", field.getFieldName());
                }
                continue;
            }
            checkValueType(field, value);
            result.put(field.getFieldKey(), value);
        }
        return result;
    }

    /* ==================== 内部方法 ==================== */

    private void ensureModel(Long siteId, String key, String name, int sort, List<SeedField> seeds) {
        CmsModel model = selectByKey(siteId, key);
        if (model == null) {
            model = new CmsModel();
            model.setSiteId(siteId);
            model.setModelKey(key);
            model.setModelName(name);
            model.setModelType(key);
            model.setBuiltIn(BUILT_IN_YES);
            model.setStatus(CommonConstants.STATUS_NORMAL);
            model.setSort(sort);
            model.setRemark("内置模型：" + name);
            model.setCreateBy(currentUserId());
            modelMapper.insert(model);
        }
        for (SeedField seed : seeds) {
            Long count = fieldMapper.selectCount(new LambdaQueryWrapper<CmsModelField>()
                    .eq(CmsModelField::getModelId, model.getModelId())
                    .eq(CmsModelField::getFieldKey, seed.key));
            if (count != null && count > 0) {
                continue;
            }
            CmsModelField field = new CmsModelField();
            field.setModelId(model.getModelId());
            field.setSiteId(siteId);
            field.setFieldKey(seed.key);
            field.setFieldName(seed.name);
            field.setFieldType(seed.type);
            field.setRequired(seed.required ? "1" : "0");
            field.setListVisible(seed.listVisible ? "1" : "0");
            field.setSearchable(seed.searchable ? "1" : "0");
            field.setSort(seed.sort);
            field.setStatus(CommonConstants.STATUS_NORMAL);
            field.setCreateBy(currentUserId());
            fieldMapper.insert(field);
        }
    }

    private CmsModel selectByKey(Long siteId, String key) {
        return modelMapper.selectOne(new LambdaQueryWrapper<CmsModel>()
                .eq(CmsModel::getSiteId, siteId)
                .eq(CmsModel::getModelKey, key));
    }

    private CmsModelField mustField(Long modelId, Long fieldId) {
        mustGet(modelId);
        CmsModelField field = fieldId == null ? null : fieldMapper.selectById(fieldId);
        if (field == null || !Objects.equals(field.getModelId(), modelId)) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.field.notfound");
        }
        return field;
    }

    private String validateType(String type) {
        if (!TYPE_ARTICLE.equals(type) && !TYPE_GALLERY.equals(type) && !TYPE_PAGE.equals(type)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.model.type.invalid");
        }
        return type;
    }

    private void validateFieldType(CmsModelField field) {
        if (!FIELD_TYPES.contains(field.getFieldType())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.field.type.invalid");
        }
        if (OPTION_TYPES.contains(field.getFieldType())
                && !StringUtils.hasText(field.getOptionsJson())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.field.options.required");
        }
    }

    private void ensureKeyUnique(Long siteId, String modelKey, Long excludeId) {
        Long count = modelMapper.selectCount(new LambdaQueryWrapper<CmsModel>()
                .eq(CmsModel::getSiteId, siteId)
                .eq(CmsModel::getModelKey, modelKey)
                .ne(excludeId != null, CmsModel::getModelId, excludeId));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.model.key.duplicate");
        }
    }

    private void ensureFieldKeyUnique(Long modelId, String fieldKey, Long excludeId) {
        Long count = fieldMapper.selectCount(new LambdaQueryWrapper<CmsModelField>()
                .eq(CmsModelField::getModelId, modelId)
                .eq(CmsModelField::getFieldKey, fieldKey)
                .ne(excludeId != null, CmsModelField::getFieldId, excludeId));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.field.key.duplicate");
        }
    }

    private long countCategories(Long modelId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getModelId, modelId));
        return count == null ? 0L : count;
    }

    private long countPages(Long modelId) {
        Long count = pageMapper.selectCount(new LambdaQueryWrapper<CmsPage>()
                .eq(CmsPage::getModelId, modelId));
        return count == null ? 0L : count;
    }

    private void checkValueType(CmsModelField field, Object value) {
        String type = field.getFieldType();
        if (MULTI_VALUE_TYPES.contains(type)) {
            if (!(value instanceof Collection)) {
                throw invalidField(field);
            }
            for (Object item : (Collection<?>) value) {
                if (item != null && !(item instanceof String)) {
                    throw invalidField(field);
                }
            }
            return;
        }
        if ("image".equals(type) || "file".equals(type)) {
            if (!(value instanceof String)) {
                throw invalidField(field);
            }
        }
    }

    private ServiceException invalidField(CmsModelField field) {
        return ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.field.invalid", field.getFieldName());
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return !StringUtils.hasText((String) value);
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        return false;
    }

    /** 数据库 char 标志兼容 1 / Y 两种历史写法。 */
    private boolean isYes(String value) {
        return "1".equals(value) || CommonConstants.YES.equals(value);
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : CommonConstants.STATUS_NORMAL;
    }

    private String defaultFlag(String value) {
        return StringUtils.hasText(value) ? value : "0";
    }

    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private static SeedField field(String key, String name, String type, boolean required,
                                   boolean listVisible, boolean searchable, int sort) {
        return new SeedField(key, name, type, required, listVisible, searchable, sort);
    }

    /** 内置字段播种定义，避免在 ensureBuiltIn 里写一大串 setter。 */
    private static final class SeedField {
        private final String key;
        private final String name;
        private final String type;
        private final boolean required;
        private final boolean listVisible;
        private final boolean searchable;
        private final int sort;

        private SeedField(String key, String name, String type, boolean required,
                          boolean listVisible, boolean searchable, int sort) {
            this.key = key;
            this.name = name;
            this.type = type;
            this.required = required;
            this.listVisible = listVisible;
            this.searchable = searchable;
            this.sort = sort;
        }
    }
}
